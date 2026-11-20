import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { RotateCcw, ArrowRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { ErrorNotice, FieldError } from '@/components/error-notice'
import { ValidationSummary } from '@/components/validation-summary'
import { fieldIds, focusInvalidField } from '@/lib/form-validation'
import { useAuth } from '@/features/auth/store'
import { useBacktestDraftStore } from '../stores/use-backtest-draft-store'
import type { StrategyId } from '../types/backtest'
import { strategies } from '../strategies'
import { backtestApi, backtestError, BacktestRequestError, type Dataset, type StrategyInfo } from '../api'
import { useResource } from '../use-resource'
import { ApplyPersonalDefaults, PresetToolbar } from './personal-workspace'
import { configurationFromDraft } from '../personalization'

function NumberField({ id, label, value, onChange, error, min = 0, max, step = 'any' }: {
  id: string; label: string; value: number; onChange: (value: number) => void; error?: string; min?: number; max?: number; step?: string
}) {
  return <div className="space-y-2"><Label htmlFor={id}>{label}</Label><Input id={id} type="number" value={Number.isNaN(value) ? '' : value} onChange={event => onChange(event.target.valueAsNumber)} min={min} max={max} step={step} required aria-invalid={!!error} aria-describedby={error ? id + '-error' : undefined} /><FieldError id={id + '-error'} message={error} /></div>
}

export function BacktestDraftPanel() {
  const owner = useAuth(state => state.user?.id)
  return <DraftForm key={owner} />
}

function DraftForm() {
  const draft = useBacktestDraftStore()
  const { datasetId, update } = draft
  const owner = useAuth(state => state.user?.id)
  const datasets = useResource<Dataset[]>('/api/datasets')
  const catalogue = useResource<StrategyInfo[]>('/api/strategies')
  const [pending, setPending] = useState(false), [notice, setNotice] = useState(''), [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const active = useRef<AbortController | null>(null)
  const navigate = useNavigate()
  useEffect(() => () => active.current?.abort(), [owner])
  useEffect(() => {
    if (!datasetId && datasets.data?.length) {
      const data = datasets.data[0]
      update({ datasetId: data.id, rangeStart: data.coverageStart.slice(0, 16), rangeEnd: new Date(Math.min(Date.parse(data.coverageEnd), Date.parse(data.coverageStart) + 7 * 86400000)).toISOString().slice(0, 16) })
    }
  }, [datasetId, datasets.data, update])
  const selected = catalogue.data?.find(item => item.id === draft.strategy)

  function edited(id: string) {
    setError('')
    setFieldErrors(current => Object.fromEntries(Object.entries(current).filter(([field]) => fieldIds[field] !== id && field !== 'parameters')))
  }

  async function submit(event: FormEvent) {
    event.preventDefault(); if (pending) return
    const controller = new AbortController(); active.current = controller
    setPending(true); setError(''); setFieldErrors({}); setNotice('')
    try {
      const result = await backtestApi.run(configurationFromDraft(draft), controller.signal)
      if (!controller.signal.aborted && useAuth.getState().user?.id === owner) navigate('/workspace/backtests/' + result.id)
    } catch (failure) {
      if (!controller.signal.aborted) {
        setError(backtestError(failure))
        if (failure instanceof BacktestRequestError) { setFieldErrors(failure.fields); focusInvalidField(failure.fields) }
      }
    } finally { if (!controller.signal.aborted) setPending(false) }
  }

  return <Card aria-labelledby="draft-title">
    <CardHeader><CardTitle id="draft-title" className="text-lg">Your strategy draft</CardTitle><CardDescription>Set the assumptions for one reproducible run.</CardDescription></CardHeader>
    <form onSubmit={submit} aria-busy={pending} onChange={event => edited((event.target as HTMLElement).id)} onInvalid={event => {
      const input: EventTarget = event.target
      if (!(input instanceof HTMLInputElement) && !(input instanceof HTMLSelectElement)) return
      const key = Object.keys(fieldIds).find(field => fieldIds[field] === input.id)
      if (!key) return
      event.preventDefault()
      setFieldErrors(current => ({ ...current, [key]: input.validity.valueMissing ? 'Enter a value for this field.' : input.validationMessage }))
      const form = input.form
      requestAnimationFrame(() => form?.querySelector<HTMLElement>(':invalid')?.focus())
    }}>
      <fieldset disabled={pending} className="min-w-0 space-y-6">
        <CardContent className="space-y-6">
          <PresetToolbar />
          <div className="space-y-2"><Label htmlFor="strategy">Strategy</Label><NativeSelect id="strategy" value={draft.strategy} aria-invalid={!!(fieldErrors.strategy || fieldErrors.parameters)} aria-describedby={fieldErrors.strategy || fieldErrors.parameters ? 'strategy-error' : undefined} onChange={event => { draft.setStrategy(event.target.value as StrategyId); setFieldErrors({}); setNotice('') }}>{strategies.map(item => <NativeSelectOption key={item.id} value={item.id}>{item.name}</NativeSelectOption>)}</NativeSelect><FieldError id="strategy-error" message={fieldErrors.strategy || fieldErrors.parameters} /><p className="text-sm leading-6 text-muted-foreground">{strategies.find(item => item.id === draft.strategy)?.description}</p></div>
          {draft.strategy === 'ma-crossover' && <div className="grid gap-4 sm:grid-cols-2"><NumberField id="fast-period" label="Fast SMA period" value={draft.fastPeriod} error={fieldErrors['parameters.fastPeriod']} onChange={fastPeriod => draft.update({ fastPeriod })} min={2} step="1" /><NumberField id="slow-period" label="Slow SMA period" value={draft.slowPeriod} error={fieldErrors['parameters.slowPeriod']} onChange={slowPeriod => draft.update({ slowPeriod })} min={3} step="1" /></div>}
          {draft.strategy === 'rsi' && <div className="grid gap-4 sm:grid-cols-3"><NumberField id="rsi-period" label="RSI period" value={draft.period} error={fieldErrors['parameters.period']} onChange={period => draft.update({ period })} min={2} step="1" /><NumberField id="rsi-lower" label="Lower threshold" value={draft.lowerThreshold} error={fieldErrors['parameters.lowerThreshold']} onChange={lowerThreshold => draft.update({ lowerThreshold })} min={1} max={99} /><NumberField id="rsi-upper" label="Upper threshold" value={draft.upperThreshold} error={fieldErrors['parameters.upperThreshold']} onChange={upperThreshold => draft.update({ upperThreshold })} min={1} max={99} /></div>}
          <div className="space-y-2"><Label htmlFor="dataset">Historical dataset</Label><NativeSelect id="dataset" required value={draft.datasetId} aria-invalid={!!fieldErrors.datasetId} aria-describedby={fieldErrors.datasetId ? 'dataset-error' : undefined} onChange={event => draft.update({ datasetId: event.target.value })}><option value="">{datasets.loading ? 'Loading datasets…' : 'Select a dataset'}</option>{datasets.data?.map(data => <option key={data.id} value={data.id}>{data.symbol} · {data.timeframe}</option>)}</NativeSelect><FieldError id="dataset-error" message={fieldErrors.datasetId} />{datasets.data?.length === 0 && <p className="text-sm leading-6 text-muted-foreground">No datasets are connected yet. A historical dataset is required to run a backtest.</p>}</div>
          <div className="grid gap-4 sm:grid-cols-2">{(['rangeStart', 'rangeEnd'] as const).map((field, index) => <div key={field} className="space-y-2"><Label htmlFor={fieldIds[field]}>{index === 0 ? 'Start' : 'End'} (UTC, inclusive)</Label><Input id={fieldIds[field]} type="datetime-local" step="3600" value={draft[field]} onChange={event => draft.update({ [field]: event.target.value })} required aria-invalid={!!fieldErrors[field]} aria-describedby={fieldErrors[field] ? fieldIds[field] + '-error' : undefined} /><FieldError id={fieldIds[field] + '-error'} message={fieldErrors[field]} /></div>)}</div>
          <ApplyPersonalDefaults />
          <div className="grid gap-4 sm:grid-cols-2"><NumberField id="capital" label="Starting capital (USDT)" value={draft.initialCapital} error={fieldErrors.initialCapital} onChange={initialCapital => draft.update({ initialCapital })} min={.00000001} max={1000000000} /><NumberField id="allocation" label="Available cash per entry (%)" value={draft.allocationPercent} error={fieldErrors.allocationPercent} onChange={allocationPercent => draft.update({ allocationPercent })} min={.01} max={100} /><NumberField id="fee-bps" label="Fee (basis points)" value={draft.feeBps} error={fieldErrors.feeBps} onChange={feeBps => draft.update({ feeBps })} max={9999} /><NumberField id="slippage-bps" label="Slippage (basis points)" value={draft.slippageBps} error={fieldErrors.slippageBps} onChange={slippageBps => draft.update({ slippageBps })} max={9999} /></div>
          <p className="text-xs leading-5 text-muted-foreground">100 basis points = 1%. Entries use the selected share of available cash, including fees. Up to 10,000 hourly candles per run.</p>
          {(datasets.error || catalogue.error) && <ErrorNotice title="Research data could not load" description={datasets.error || catalogue.error || ''} action={{ label: 'Try again', onClick: () => { datasets.reload(); catalogue.reload() } }} />}
          <ValidationSummary fields={fieldErrors} />
          {error && !Object.keys(fieldErrors).length && <ErrorNotice title="Backtest could not run" description={error} />}
        </CardContent>
        <CardFooter className="flex-col items-stretch gap-4 border-t pt-5"><div className="flex flex-wrap justify-between gap-3"><Button type="button" variant="outline" onClick={() => { draft.reset(); setNotice('Draft reset to moving-average crossover.'); setError(''); setFieldErrors({}) }}><RotateCcw aria-hidden="true" />Reset draft</Button><Button type="submit" disabled={!selected?.available || !draft.datasetId || datasets.loading || catalogue.loading} aria-describedby="execution-note">{pending ? 'Running…' : 'Run backtest'}<ArrowRight aria-hidden="true" /></Button></div><p id="execution-note" className="text-xs leading-5 text-muted-foreground">{selected?.available ? 'Runs finish in this request. Results and assumptions are saved together.' : 'This strategy is not available to run yet. You can prepare its settings now.'}</p><p role="status" className="min-h-5 text-xs leading-5 text-positive">{notice}</p></CardFooter>
      </fieldset>
    </form>
  </Card>
}
