import { Link } from 'react-router'
import { ArrowRight, Database } from 'lucide-react'
import { Card,CardHeader,CardTitle,CardDescription,CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Table,TableBody,TableRow,TableHead,TableCell } from '@/components/ui/table'
import { BacktestDraftPanel } from '../components/backtest-draft-panel'
import { PageHeading } from '@/features/workspace/pages/workspace-pages'
import { useResource } from '../use-resource'
import type { Dataset } from '../api'
export function BacktestWorkspacePage(){
  const resource=useResource<Dataset[]>('/api/datasets')
  const dataset=resource.data?.[0]
  return (
    <>
      <PageHeading title="Workspace" description="Choose a strategy and prepare your next experiment." action={<Badge variant="secondary">Draft mode</Badge>} />
      <div className="grid items-start gap-7 xl:grid-cols-[1.15fr_1fr]">
        <BacktestDraftPanel/>
        <div className="space-y-6">
          <Card><CardHeader><div className="flex items-center gap-2"><Database className="size-4 text-primary" aria-hidden="true"/><CardTitle>Research scope</CardTitle></div><CardDescription>{dataset?'The server-connected historical dataset. Inspect its coverage and quality before running.':'Connect a historical dataset before running your first backtest.'}</CardDescription></CardHeader><CardContent><Table aria-label="Research scope"><TableBody>{[['Market',dataset?.symbol??'BTCUSDT spot'],['Timeframe',dataset?.timeframe??'1 hour'],['Coverage',dataset?dataset.coverageStart.slice(0,10)+' to '+dataset.coverageEnd.slice(0,10):'Not connected']].map(([label,value])=><TableRow key={label}><TableHead scope="row">{label}</TableHead><TableCell className="text-right whitespace-normal">{value}</TableCell></TableRow>)}</TableBody></Table></CardContent></Card>
          <section className="px-1 pt-2" aria-labelledby="first-run-title"><h2 id="first-run-title" className="text-base font-semibold">Before your first run</h2><div className="mt-4 divide-y border-y">{[['/workspace/datasets','Get to know your data','Check coverage and sources before testing.'],['/workspace/strategies','Explore an approach','Choose a baseline or a signal to investigate.'],['/workspace/backtests','Keep your experiments together','Review saved settings, trades and equity after a completed run.']].map(([href,title,description])=><Link key={href} to={href} className="group flex items-center justify-between gap-4 py-4"><span><span className="text-sm font-medium group-hover:text-primary">{title}</span><span className="mt-1 block text-xs leading-5 text-muted-foreground">{description}</span></span><ArrowRight className="size-4 shrink-0 text-muted-foreground group-hover:text-primary" aria-hidden="true"/></Link>)}</div></section>
        </div>
      </div>
    </>
  )
}
