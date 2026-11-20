import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ArrowRight, Download, Copy } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { ErrorNotice } from '@/components/error-notice'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableHeader, TableBody, TableCell, TableHead, TableRow } from '@/components/ui/table'
import { PageHeading } from '@/features/workspace/pages/workspace-pages'
import { useAuth } from '@/features/auth/store'
import { useResource } from '../use-resource'
import { BacktestCharts } from '../components/backtest-charts'
import { ExperimentEditor } from '../components/personal-workspace'
import { useBacktestDraftStore } from '../stores/use-backtest-draft-store'
import { draftFromConfiguration, emptyId, type RunAnnotation } from '../personalization'
import type { Dataset, History, SavedRun } from '../api'

const money = (value: number) => value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const percent = (value: number) => (value * 100).toFixed(2) + '%'
const strategyName = (id: string) => ({ 'ma-crossover': 'SMA crossover', 'buy-and-hold': 'Buy and hold', rsi: 'RSI' })[id] ?? id
function Status({ loading, error, reload }: { loading: boolean; error?: string; reload: () => void }) {
  return loading ? <div role="status" aria-label="Loading research data" className="space-y-4"><Skeleton className="h-8 w-48" /><Skeleton className="h-40 w-full" /></div> : error ? <ErrorNotice title="Research data could not load" description={error} action={{ label: 'Try again', onClick: reload }} /> : null
}

export function DatasetBrowserPage(){
  const resource=useResource<Dataset[]>('/api/datasets')
  return <><PageHeading title="Datasets" description="Inspect coverage and quality before choosing a historical range."/><Status {...resource}/>{resource.data?.length===0&&<div className="rounded-xl border p-8"><h2 className="text-xl font-semibold">No datasets are connected yet.</h2><p className="mt-3 max-w-xl text-sm leading-6 text-muted-foreground">A historical dataset must be configured on the server before running a backtest. No market data is uploaded from your browser.</p></div>}{resource.data?.map(data=><section key={data.id} className="mb-6 rounded-xl border p-6"><h2 className="text-lg font-semibold">{data.symbol} / {data.timeframe}</h2><dl className="mt-5 grid gap-5 text-sm sm:grid-cols-2"><div><dt className="text-muted-foreground">Coverage (UTC)</dt><dd className="mt-1">{data.coverageStart.slice(0,16).replace('T',' ')} — {data.coverageEnd.slice(0,16).replace('T',' ')}</dd></div><div><dt className="text-muted-foreground">Candles</dt><dd className="mt-1 tabular-nums">{data.candleCount.toLocaleString()}</dd></div><div><dt className="text-muted-foreground">Source</dt><dd className="mt-1 break-words">{data.source}</dd></div><div><dt className="text-muted-foreground">Missing hours</dt><dd className="mt-1">{data.missingHours}. Ranges crossing missing candles are rejected.</dd></div><div className="sm:col-span-2"><dt className="text-muted-foreground">SHA-256 fingerprint</dt><dd className="mt-1 break-all font-mono text-xs">{data.fingerprint}</dd></div></dl><Button variant="outline" className="mt-6" asChild><Link to="/workspace">Configure a backtest<ArrowRight aria-hidden="true"/></Link></Button></section>)}</>
}
// Selection belongs to this account; pagination does not clear selected runs.
export function RunHistoryPage() {
  const owner = useAuth(state => state.user?.id)
  return <RunHistory key={owner} />
}
function RunHistory() {
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<string[]>([])
  const resource = useResource<History>(`/api/backtests?page=${page}&size=10`)
  const ids = resource.data?.items.map(run => run.id) ?? []
  const metadata = useResource<RunAnnotation[]>(`/api/workspace/annotations?ids=${ids.join(',') || emptyId}`)
  const annotations = new Map(metadata.data?.map(item => [item.id, item.annotation]))
  const comparisonUrl = '/workspace/compare?ids=' + selected.join(',')
  return <>
    <PageHeading title="Backtests" description="Your saved experiments and the assumptions behind each result." action={<Button asChild><Link to="/workspace">New backtest<ArrowRight aria-hidden="true" /></Link></Button>} />
    <Status {...resource} />
    {resource.data?.total === 0 && <div className="rounded-xl border p-8"><h2 className="text-xl font-semibold">The first experiment is still ahead.</h2><p className="mt-3 text-sm text-muted-foreground">Prepare a strategy draft. Completed runs will appear here.</p></div>}
    {resource.data && resource.data.total > 0 && <>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3"><p className="text-sm text-muted-foreground">Select two to four experiments to compare. {selected.length} selected.</p><div className="flex gap-2">{selected.length > 0 && <Button variant="ghost" onClick={() => setSelected([])}>Clear selection</Button>}{selected.length >= 2 ? <Button variant="outline" asChild><Link to={comparisonUrl}>Compare selected</Link></Button> : <Button variant="outline" disabled>Compare selected</Button>}</div></div>
      {metadata.error && <ErrorNotice className="mb-4" title="Experiment names could not load" description="Results are still available. You can open and compare runs while names are unavailable." action={{ label: 'Retry names', onClick: metadata.reload }} />}
      <Table aria-label="Saved backtests">
        <TableHeader><TableRow><TableHead><span className="sr-only">Compare</span></TableHead><TableHead>Experiment</TableHead><TableHead className="text-right">Final equity</TableHead><TableHead className="text-right">Return</TableHead><TableHead>Completed</TableHead></TableRow></TableHeader>
        <TableBody>{resource.data.items.map(run => {
          const annotation = annotations.get(run.id)
          return <TableRow key={run.id} data-state={selected.includes(run.id) ? 'selected' : undefined} className="data-[state=selected]:bg-muted/50">
            <TableCell><label className="flex min-h-11 min-w-8 items-center"><Input type="checkbox" className="size-4 rounded border accent-primary focus-visible:ring-2" aria-label={'Compare ' + (annotation?.title || strategyName(run.strategy))} checked={selected.includes(run.id)} disabled={selected.length >= 4 && !selected.includes(run.id)} onChange={event => setSelected(current => event.target.checked ? [...current, run.id] : current.filter(id => id !== run.id))} /></label></TableCell>
            <TableCell className="max-w-72 whitespace-normal"><Link className="inline-flex min-h-11 items-center break-words font-medium text-primary underline-offset-4 hover:underline" to={'/workspace/backtests/' + run.id}>{annotation?.title || strategyName(run.strategy)}</Link>{annotation?.title && <p className="text-xs text-muted-foreground">{strategyName(run.strategy)}</p>}{!!annotation?.tags.length && <p className="mt-1 break-words text-xs text-muted-foreground">{annotation.tags.join(' / ')}</p>}</TableCell>
            <TableCell className="text-right tabular-nums">{money(run.finalEquity)} USDT</TableCell><TableCell className="text-right tabular-nums">{percent(run.totalReturn)}</TableCell><TableCell className="text-xs text-muted-foreground">{new Date(run.completedAt).toLocaleString()}</TableCell>
          </TableRow>
        })}</TableBody>
      </Table>
      <div className="mt-6 flex flex-wrap items-center justify-between gap-3"><Button variant="outline" disabled={page === 0} onClick={() => setPage(value => value - 1)}>Previous</Button><span className="text-sm text-muted-foreground">Page {page + 1} / {resource.data.total} saved runs</span><Button variant="outline" disabled={(page + 1) * 10 >= resource.data.total} onClick={() => setPage(value => value + 1)}>Next</Button></div>
    </>}
  </>
}

export function RunDetailPage() {
  const { id } = useParams()
  const resource = useResource<SavedRun>('/api/backtests/' + id)
  const metadata = useResource<RunAnnotation[]>(`/api/workspace/annotations?ids=${id || emptyId}`)
  const run = resource.data
  const navigate = useNavigate()
  function download() {
    if (!run) return
    const payload = { ...run, annotation: metadata.data?.[0]?.annotation }
    const url = URL.createObjectURL(new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' }))
    const link = document.createElement('a'); link.href = url; link.download = 'testragem-' + run.id + '.json'; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
  }
  function reuse() {
    if (!run) return
    useBacktestDraftStore.getState().reset()
    useBacktestDraftStore.getState().update(draftFromConfiguration(run.configuration))
    navigate('/workspace')
  }
  return <>
    <PageHeading title="Backtest result" description={metadata.data?.[0]?.annotation.title || 'A completed historical simulation. Open positions are valued at the final close.'} action={run && <div className="flex flex-wrap gap-2"><Button variant="outline" onClick={reuse}><Copy aria-hidden="true" />Reuse settings</Button><Button variant="outline" onClick={download}><Download aria-hidden="true" />Export JSON</Button></div>} />
    <Status {...resource} />
    {run && <>
      <dl className="grid grid-cols-2 gap-x-4 gap-y-6 border-b pb-7 xl:grid-cols-4">{[['Final equity', money(run.result.finalEquity) + ' USDT'], ['Total return', percent(run.result.totalReturn)], ['Maximum drawdown', percent(run.result.maxDrawdown)], ['Fees / slippage', money(run.result.totalFees) + ' / ' + money(run.result.totalSlippage) + ' USDT']].map(([label, value]) => <div key={label}><dt className="text-sm text-muted-foreground">{label}</dt><dd className="mt-2 text-xl font-semibold tabular-nums">{value}</dd></div>)}</dl>
      <BacktestCharts key={run.id} run={run} />
      <ExperimentEditor key={run.id} id={run.id} onSaved={metadata.reload} />
      <section className="mt-8 border-t pt-7" aria-labelledby="assumptions-title"><h2 id="assumptions-title" className="text-lg font-semibold">Run assumptions</h2><dl className="mt-4 grid gap-4 text-sm sm:grid-cols-2">{[['Strategy', run.configuration.strategy + ' / ' + run.configuration.strategyVersion], ['Engine', run.result.engineVersion], ['Capital', money(run.configuration.initialCapital) + ' USDT'], ['Allocation', run.configuration.allocationPercent + '% of available cash'], ['Costs', run.configuration.feeBps + ' bps fees / ' + run.configuration.slippageBps + ' bps slippage'], ['Range (UTC)', run.configuration.rangeStart + ' to ' + run.configuration.rangeEnd], ['Parameters', JSON.stringify(run.configuration.parameters)], ['Dataset fingerprint', run.datasetFingerprint]].map(([label, value]) => <div key={label}><dt className="text-muted-foreground">{label}</dt><dd className="mt-1 break-all">{value}</dd></div>)}</dl><Button variant="link" className="mt-4 px-0" asChild><Link to="/workspace/backtests">Back to history</Link></Button></section>
    </>}
  </>
}
