import { useEffect, useRef, useState } from 'react'
import { Save, RotateCcw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { NativeSelect } from '@/components/ui/native-select'
import { Textarea } from '@/components/ui/textarea'
import { Skeleton } from '@/components/ui/skeleton'
import { ErrorNotice, FieldError } from '@/components/error-notice'
import { ValidationSummary } from '@/components/validation-summary'
import { fieldIds, focusInvalidField } from '@/lib/form-validation'
import { backtestApi, backtestError, BacktestRequestError } from '../api'
import { useResource } from '../use-resource'
import { useBacktestDraftStore } from '../stores/use-backtest-draft-store'
import { configurationFromDraft, draftFromConfiguration, emptyAnnotation, emptyId, type Annotation, type Preferences, type Preset, type RunAnnotation } from '../personalization'

function message(error: unknown) {
  return error instanceof BacktestRequestError && Object.keys(error.fields).length
    ? Object.values(error.fields).join(' ') : backtestError(error)
}

// Abort mutations when the account-scoped parent unmounts, including during sign-out.
function useMutation(targets: Record<string, string> = fieldIds) {
  const active = useRef<AbortController | null>(null)
  const [pending, setPending] = useState(false)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const [fields, setFields] = useState<Record<string, string>>({})
  useEffect(() => () => active.current?.abort(), [])
  async function run(action: (signal: AbortSignal) => Promise<void>, success: string) {
    if (pending) return
    const controller = new AbortController(); active.current = controller
    setPending(true); setError(''); setFields({}); setNotice('')
    try { await action(controller.signal); if (!controller.signal.aborted) setNotice(success) }
    catch (failure) { if (!controller.signal.aborted) { setError(message(failure)); if (failure instanceof BacktestRequestError) { setFields(failure.fields); focusInvalidField(failure.fields, targets) } } }
    finally { if (!controller.signal.aborted) setPending(false) }
  }
  function clear(field: string) {
    setError(''); setNotice('')
    setFields(current => Object.fromEntries(Object.entries(current).filter(([key]) => key !== field)))
  }
  function invalid(event: { target: EventTarget; preventDefault: () => void }) {
    const input = event.target
    if (!(input instanceof HTMLInputElement) && !(input instanceof HTMLSelectElement) && !(input instanceof HTMLTextAreaElement)) return
    const key = Object.keys(targets).find(field => targets[field] === input.id)
    if (!key) return
    event.preventDefault(); setError('')
    setFields(current => ({ ...current, [key]: input.validity.valueMissing ? 'Enter a value for this field.' : input.validationMessage }))
    requestAnimationFrame(() => input.form?.querySelector<HTMLElement>(':invalid')?.focus())
  }
  return { pending, notice, error, fields, run, clear, invalid }
}

function Feedback({ error, notice, fields = {}, targets }: { error?: string; notice?: string; fields?: Record<string, string>; targets?: Record<string, string> }) {
  return <><ValidationSummary fields={fields} targets={targets} />{error && !Object.keys(fields).length && <ErrorNotice title="Changes could not be saved" description={error} />}{notice && <p role="status" className="text-sm text-positive">{notice}</p>}</>
}

export function PresetToolbar() {
  const draft = useBacktestDraftStore()
  const presets = useResource<Preset[]>('/api/workspace/presets')
  const [editedName, setName] = useState<string | null>(null)
  const name = editedName ?? presets.data?.find(preset => preset.id === draft.presetId)?.name ?? ''
  const mutation = useMutation()
  const history = useResource<Preset[]>(draft.presetId ? `/api/workspace/presets/${draft.presetId}/versions` : '/api/workspace/presets')
  const versions = draft.presetId ? history.data ?? [] : []
  const loadingVersions = draft.presetId && history.loading
  const versionError = draft.presetId ? history.error : undefined
  function load(preset: Preset) {
    draft.update(draftFromConfiguration(preset.configuration))
    useBacktestDraftStore.setState({ presetId: preset.id, presetVersion: preset.version })
    setName(preset.name)
  }
  function save(asNew: boolean) {
    void mutation.run(async signal => {
      const path = !asNew && draft.presetId ? `/api/workspace/presets/${draft.presetId}/versions` : '/api/workspace/presets'
      const preset = await backtestApi.post<Preset>(path, { name, configuration: configurationFromDraft(draft) }, signal)
      if (signal.aborted) return
      load(preset); presets.reload(); history.reload()
    }, 'Preset saved. Previous versions are preserved.')
  }
  return <section aria-labelledby="presets-title" className="space-y-4 border-b pb-6">
    <div><h2 id="presets-title" className="text-sm font-medium">Saved presets</h2><p className="mt-1 text-xs leading-5 text-muted-foreground">Reuse your settings. Saving a revision keeps earlier versions intact.</p></div>
    <div className="space-y-2"><Label htmlFor="saved-preset">Load a preset</Label><NativeSelect id="saved-preset" disabled={presets.loading || mutation.pending} value={draft.presetId} onChange={event => {
      const preset = presets.data?.find(item => item.id === event.target.value)
      if (preset) load(preset)
      else { useBacktestDraftStore.setState({ presetId: '', presetVersion: 0 }); setName('') }
    }}><option value="">{presets.loading ? 'Loading presets…' : 'Unsaved draft'}</option>{presets.data?.map(preset => <option key={preset.id} value={preset.id}>{preset.name} · v{preset.version}</option>)}</NativeSelect></div>
    {draft.presetId && <div className="space-y-2"><Label htmlFor="preset-version">Version</Label><NativeSelect id="preset-version" disabled={loadingVersions || mutation.pending} value={draft.presetVersion} onChange={event => { const version = versions.find(item => item.version === Number(event.target.value)); if (version) load(version) }}><option value={draft.presetVersion}>Version {draft.presetVersion}</option>{versions.filter(item => item.version !== draft.presetVersion).map(item => <option key={item.version} value={item.version}>Version {item.version} · {new Date(item.createdAt).toLocaleDateString()}</option>)}</NativeSelect></div>}
    <div className="space-y-2"><Label htmlFor="preset-name">Preset name</Label><Input id="preset-name" maxLength={100} value={name} onChange={event => { setName(event.target.value); mutation.clear('name') }} aria-invalid={!!mutation.fields.name} aria-describedby={mutation.fields.name ? 'preset-name-error' : undefined} placeholder="For example, BTC trend research" /><FieldError id="preset-name-error" message={mutation.fields.name} /></div>
    <div className="flex flex-wrap gap-2"><Button type="button" variant="outline" disabled={mutation.pending || !name.trim() || !draft.datasetId || !draft.rangeStart || !draft.rangeEnd} onClick={event => { if (event.currentTarget.form?.reportValidity()) save(false) }}><Save aria-hidden="true" />{mutation.pending ? 'Saving…' : draft.presetId ? 'Save new version' : 'Save preset'}</Button>{draft.presetId && <Button type="button" variant="ghost" disabled={mutation.pending || !name.trim()} onClick={event => { if (event.currentTarget.form?.reportValidity()) save(true) }}>Save as separate preset</Button>}</div>
    {presets.error && <ErrorNotice title="Presets could not load" description={presets.error} action={{ label: 'Retry presets', onClick: presets.reload }} />}
    <Feedback error={mutation.error} notice={mutation.notice} fields={mutation.fields} />
    {versionError && <ErrorNotice title="Preset history could not load" description={versionError} action={{ label: 'Retry versions', onClick: history.reload }} />}
  </section>
}

export function ApplyPersonalDefaults() {
  const preferences = useResource<Preferences>('/api/workspace/preferences')
  const update = useBacktestDraftStore(state => state.update)
  const [applied, setApplied] = useState(false)
  return <div className="space-y-2"><div className="flex flex-wrap items-center justify-between gap-2"><span className="text-sm font-medium">Trading assumptions</span><Button type="button" variant="ghost" size="sm" disabled={!preferences.data} onClick={() => { if (preferences.data) { const { evaluationGoal: _goal, ...financial } = preferences.data; update(financial); setApplied(true) } }}><RotateCcw aria-hidden="true" />Use my defaults</Button></div>{preferences.error && <ErrorNotice title="Defaults could not load" description="Your current draft is still available. Retry to load your saved assumptions." action={{ label: 'Retry defaults', onClick: preferences.reload }} />}{applied && <p role="status" className="text-xs text-positive">Your saved financial defaults have been applied.</p>}</div>
}

export function PreferencesPanel() {
  const resource = useResource<Preferences>('/api/workspace/preferences')
  return <section className="mt-10 border-t pt-7" aria-labelledby="defaults-title"><h2 id="defaults-title" className="text-base font-semibold">Research defaults</h2><p className="mt-2 text-sm leading-6 text-muted-foreground">Save assumptions for future drafts. Apply them from the workspace; existing runs keep their original settings.</p>{resource.loading ? <Skeleton className="mt-5 h-40 w-full" /> : resource.error ? <ErrorNotice className="mt-4" title="Defaults could not load" description={resource.error} action={{ label: 'Try again', onClick: resource.reload }} /> : resource.data && <PreferencesForm initial={resource.data} />}</section>
}

function PreferencesForm({ initial }: { initial: Preferences }) {
  const [settings, setSettings] = useState(initial)
  const targets = { ...fieldIds, initialCapital: 'default-initialCapital', feeBps: 'default-feeBps', slippageBps: 'default-slippageBps', allocationPercent: 'default-allocationPercent' }
  const mutation = useMutation(targets)
  return <form className="mt-5 space-y-5" onInvalid={mutation.invalid} onSubmit={event => { event.preventDefault(); void mutation.run(async signal => { const saved = await backtestApi.put<Preferences>('/api/workspace/preferences', settings, signal); if (!signal.aborted) setSettings(saved) }, 'Research defaults saved.') }}>
    <fieldset disabled={mutation.pending} className="grid min-w-0 gap-4 sm:grid-cols-2">{([
      ['initialCapital', 'Starting capital (USDT)', 0.00000001, 1000000000], ['allocationPercent', 'Available cash per entry (%)', 0.00000001, 100], ['feeBps', 'Fee (basis points)', 0, 9999.999999], ['slippageBps', 'Slippage (basis points)', 0, 9999.999999],
    ] as const).map(([field, label, min, max]) => <div key={field} className="space-y-2"><Label htmlFor={'default-' + field}>{label}</Label><Input id={'default-' + field} type="number" min={min} max={max} step="any" required value={Number.isNaN(settings[field]) ? '' : settings[field]} onChange={event => { setSettings({ ...settings, [field]: event.target.valueAsNumber }); mutation.clear(field) }} aria-invalid={!!mutation.fields[field]} aria-describedby={mutation.fields[field] ? 'default-' + field + '-error' : undefined} /><FieldError id={'default-' + field + '-error'} message={mutation.fields[field]} /></div>)}
      <div className="space-y-2 sm:col-span-2"><Label htmlFor="evaluation-goal">Comparison focus</Label><NativeSelect id="evaluation-goal" value={settings.evaluationGoal} onChange={event => (setSettings({ ...settings, evaluationGoal: event.target.value as Preferences['evaluationGoal'] }), mutation.clear('evaluationGoal'))}><option value="return">Total return</option><option value="drawdown">Maximum drawdown</option><option value="costs">Fees and slippage</option></NativeSelect><p className="text-xs leading-5 text-muted-foreground">Highlights a metric in comparisons. It does not automatically choose a strategy.</p></div>
    </fieldset>
    <Feedback error={mutation.error} notice={mutation.notice} fields={mutation.fields} targets={targets} /><Button type="submit" disabled={mutation.pending}>{mutation.pending ? 'Saving…' : 'Save defaults'}</Button>
  </form>
}

export function ExperimentEditor({ id, onSaved }: { id: string; onSaved: () => void }) {
  const resource = useResource<RunAnnotation[]>(`/api/workspace/annotations?ids=${id || emptyId}`)
  return <section className="mt-8 border-t pt-7" aria-labelledby="experiment-title"><h2 id="experiment-title" className="text-lg font-semibold">Your experiment</h2><p className="mt-2 text-sm text-muted-foreground">Add a name, notes and tags. Simulation results stay unchanged.</p>{resource.loading ? <Skeleton className="mt-5 h-32 w-full" /> : resource.error ? <ErrorNotice className="mt-4" title="Experiment details could not load" description={resource.error} action={{ label: 'Retry experiment details', onClick: resource.reload }} /> : <AnnotationForm key={id} id={id} initial={resource.data?.[0]?.annotation ?? emptyAnnotation} onSaved={onSaved} />}</section>
}

function AnnotationForm({ id, initial, onSaved }: { id: string; initial: Annotation; onSaved: () => void }) {
  const [title, setTitle] = useState(initial.title), [notes, setNotes] = useState(initial.notes), [tags, setTags] = useState(initial.tags.join(', '))
  const mutation = useMutation()
  return <form className="mt-5 max-w-2xl space-y-4" onInvalid={mutation.invalid} onSubmit={event => { event.preventDefault(); void mutation.run(async signal => { await backtestApi.put<Annotation>(`/api/workspace/annotations/${id}`, { title, notes, tags: tags.split(',').map(tag => tag.trim()).filter(Boolean) }, signal); if (!signal.aborted) onSaved() }, 'Experiment details saved.') }}>
    <fieldset disabled={mutation.pending} className="min-w-0 space-y-4"><div className="space-y-2"><Label htmlFor="experiment-name">Experiment name</Label><Input id="experiment-name" maxLength={100} value={title} onChange={event => { setTitle(event.target.value); mutation.clear('title') }} aria-invalid={!!mutation.fields.title} aria-describedby={mutation.fields.title ? 'experiment-name-error' : undefined} placeholder="For example, January trend baseline" /><FieldError id="experiment-name-error" message={mutation.fields.title} /></div><div className="space-y-2"><Label htmlFor="experiment-notes">Research notes</Label><Textarea id="experiment-notes" maxLength={4000} value={notes} onChange={event => { setNotes(event.target.value); mutation.clear('notes') }} aria-invalid={!!mutation.fields.notes} aria-describedby={mutation.fields.notes ? 'experiment-notes-error' : undefined} placeholder="What did you test? What would you investigate next?" /><FieldError id="experiment-notes-error" message={mutation.fields.notes} /></div><div className="space-y-2"><Label htmlFor="experiment-tags">Tags</Label><Input id="experiment-tags" value={tags} onChange={event => { setTags(event.target.value); mutation.clear('tags') }} aria-invalid={!!mutation.fields.tags} aria-describedby={mutation.fields.tags ? 'tags-help experiment-tags-error' : 'tags-help'} /><p id="tags-help" className="text-xs text-muted-foreground">Separate up to eight tags with commas. Maximum 30 characters per tag.</p><FieldError id="experiment-tags-error" message={mutation.fields.tags} /></div></fieldset>
    <Feedback error={mutation.error} notice={mutation.notice} fields={mutation.fields} /><Button disabled={mutation.pending} type="submit">{mutation.pending ? 'Saving…' : 'Save experiment details'}</Button>
  </form>
}
