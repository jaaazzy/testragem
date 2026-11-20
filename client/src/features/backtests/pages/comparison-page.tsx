import { Link, useSearchParams } from 'react-router'
import { Button } from '@/components/ui/button'
import { ErrorNotice } from '@/components/error-notice'
import { Label } from '@/components/ui/label'
import { NativeSelect } from '@/components/ui/native-select'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { PageHeading } from '@/features/workspace/pages/workspace-pages'
import { useResource } from '../use-resource'
import type { SavedRun } from '../api'
import { emptyId, type Preferences, type RunAnnotation } from '../personalization'
import { useState } from 'react'

const money = (value: number) => value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const percent = (value: number) => (value * 100).toFixed(2) + '%'

export function ComparisonPage() {
  const [params] = useSearchParams()
  const ids = [...new Set((params.get('ids') ?? '').split(',').filter(Boolean))]
  const valid = ids.length >= 2 && ids.length <= 4 && ids.every(id => /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id))
  return valid ? <Comparison key={ids.join(',')} ids={ids} /> : <><PageHeading title="Compare experiments" description="Choose two to four saved backtests from your history." /><Button asChild><Link to="/workspace/backtests">Choose experiments</Link></Button></>
}

function Comparison({ ids }: { ids: string[] }) {
  const runs = useResource<SavedRun[]>('/api/backtests/compare?ids=' + ids.join(','))
  const metadata = useResource<RunAnnotation[]>('/api/workspace/annotations?ids=' + (ids.join(',') || emptyId))
  const preferences = useResource<Preferences>('/api/workspace/preferences')
  const [focus, setFocus] = useState<Preferences['evaluationGoal'] | ''>('')
  const goal = focus || preferences.data?.evaluationGoal || 'return'
  const names = new Map(metadata.data?.map(item => [item.id, item.annotation.title]))
  const first = runs.data?.[0]
  const comparable = runs.data?.every(run => first && run.datasetFingerprint === first.datasetFingerprint && run.configuration.rangeStart === first.configuration.rangeStart && run.configuration.rangeEnd === first.configuration.rangeEnd && run.configuration.initialCapital === first.configuration.initialCapital && run.configuration.feeBps === first.configuration.feeBps && run.configuration.slippageBps === first.configuration.slippageBps && run.configuration.allocationPercent === first.configuration.allocationPercent && run.result.engineVersion === first.result.engineVersion)
  const rows: { label: string; goal?: Preferences['evaluationGoal']; value: (run: SavedRun) => string }[] = [
    { label: 'Total return', goal: 'return', value: run => percent(run.result.totalReturn) },
    { label: 'Maximum drawdown', goal: 'drawdown', value: run => percent(run.result.maxDrawdown) },
    { label: 'Fees + slippage (USDT)', goal: 'costs', value: run => money(run.result.totalFees + run.result.totalSlippage) },
    { label: 'Final equity (USDT)', value: run => money(run.result.finalEquity) },
    { label: 'Starting capital (USDT)', value: run => money(run.configuration.initialCapital) },
    { label: 'Executed orders', value: run => String(run.result.fills.length) },
    { label: 'Allocation', value: run => run.configuration.allocationPercent + '%' },
    { label: 'Fee / slippage (bps)', value: run => run.configuration.feeBps + ' / ' + run.configuration.slippageBps },
  ]
  return <>
    <PageHeading title="Compare experiments" description="Review measured results and the assumptions behind them." action={<Button variant="outline" asChild><Link to="/workspace/backtests">Back to history</Link></Button>} />
    {runs.loading && <div role="status" aria-label="Loading comparison"><Skeleton className="h-72 w-full" /></div>}
    {runs.error && <ErrorNotice title="Comparison could not load" description={runs.error} action={{ label: 'Try again', onClick: runs.reload }} />}
    {runs.data && <>
      <div className="mb-6 max-w-xs space-y-2"><Label htmlFor="comparison-focus">Comparison focus</Label><NativeSelect id="comparison-focus" value={goal} onChange={event => setFocus(event.target.value as Preferences['evaluationGoal'])}><option value="return">Total return</option><option value="drawdown">Maximum drawdown</option><option value="costs">Fees and slippage</option></NativeSelect></div>
      {!comparable && <p role="status" className="mb-6 max-w-3xl rounded-lg border p-4 text-sm leading-6">These runs use different dates, datasets, capital, allocation, costs or engine versions. Differences in results cannot be attributed to strategy alone. Reuse settings to make a comparison under the same assumptions.</p>}
      {metadata.error && <ErrorNotice className="mb-4" title="Experiment names could not load" description="Comparing saved results by strategy instead." action={{ label: 'Retry names', onClick: metadata.reload }} />}
      <Table aria-label="Backtest comparison"><TableHeader><TableRow><TableHead>Metric</TableHead>{runs.data.map((run, index) => <TableHead key={run.id} className="min-w-44 max-w-64 whitespace-normal text-right"><Link className="inline-flex min-h-11 items-center justify-end break-words text-primary hover:underline" to={'/workspace/backtests/' + run.id}>{names.get(run.id) || `Experiment ${index + 1}`}</Link><span className="block text-xs font-normal">{run.configuration.strategy}</span></TableHead>)}</TableRow></TableHeader><TableBody>{rows.map(row => <TableRow key={row.label} className={row.goal === goal ? 'bg-muted/50' : undefined}><TableHead scope="row">{row.label}{row.goal === goal && <span className="sr-only">, selected focus</span>}</TableHead>{runs.data!.map(run => <TableCell key={run.id} className="text-right tabular-nums">{row.value(run)}</TableCell>)}</TableRow>)}
        <TableRow><TableHead scope="row">Range (UTC)</TableHead>{runs.data.map(run => <TableCell key={run.id} className="text-right text-xs"><span className="block">{run.configuration.rangeStart.slice(0, 16).replace('T', ' ')}</span><span className="block text-muted-foreground">to {run.configuration.rangeEnd.slice(0, 16).replace('T', ' ')}</span></TableCell>)}</TableRow>
        <TableRow><TableHead scope="row">Parameters</TableHead>{runs.data.map(run => <TableCell key={run.id} className="max-w-64 whitespace-normal text-right text-xs">{Object.entries(run.configuration.parameters).map(([key, value]) => `${key}: ${value}`).join(', ') || 'None'}</TableCell>)}</TableRow>
      </TableBody></Table>
      <p className="mt-5 max-w-3xl text-xs leading-5 text-muted-foreground">Slippage is already included in fill prices; the combined cost row is descriptive. Open positions remain valued at the final close. A selected focus highlights one metric and does not establish future performance.</p>
    </>}
  </>
}
