import { Link, useNavigate } from 'react-router'
import type { ReactNode } from 'react'
import { ArrowRight, Check, Database, History, ShieldCheck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { useAuth } from '@/features/auth/store'
import { strategies } from '@/features/backtests/strategies'
import { useBacktestDraftStore } from '@/features/backtests/stores/use-backtest-draft-store'

export function PageHeading({ title, description, action }: { title: string; description: string; action?: ReactNode }) {
  return <div className="mb-8 flex flex-wrap items-start justify-between gap-4 border-b pb-7"><div className="min-w-0 flex-1 basis-60"><h1 className="text-[1.75rem] leading-tight font-semibold tracking-tight sm:text-[2rem]">{title}</h1><p className="mt-2 max-w-[60ch] text-sm leading-6 text-muted-foreground">{description}</p></div>{action&&<div className="pt-1">{action}</div>}</div>
}

export function DatasetsPage() {
  return <>
    <PageHeading title="Datasets" description="Get to know the market history behind each experiment." action={<Badge variant="outline">Not connected</Badge>} />
    <section aria-labelledby="datasets-empty" className="rounded-xl border px-6 py-12 sm:py-16">
      <div className="mx-auto max-w-md text-center"><Database className="mx-auto mb-5 size-9 text-muted-foreground" strokeWidth={1.5} aria-hidden="true" /><h2 id="datasets-empty" className="text-xl font-semibold">Your research needs a starting point.</h2><p className="mt-3 text-sm leading-6 text-muted-foreground">No datasets are connected yet. Coverage, missing candles, and source details will appear here when dataset import is available.</p><Button variant="outline" className="mt-6" asChild><Link to="/workspace">Explore the strategy draft<ArrowRight aria-hidden="true" /></Link></Button></div>
    </section>
    <section className="mt-10" aria-labelledby="dataset-scope"><h2 id="dataset-scope" className="text-base font-semibold">The first dataset to connect</h2><p className="mt-2 text-sm leading-6 text-muted-foreground">The planned MVP starts with one market and one timeframe. These are research targets, not imported data.</p><dl className="mt-5 grid gap-5 border-y py-5 sm:grid-cols-3">{[['Market', 'BTCUSDT spot'], ['Timeframe', '1-hour candles'], ['Target coverage', '2021–2025']].map(([label, value]) => <div key={label}><dt className="text-xs text-muted-foreground">{label}</dt><dd className="mt-2 text-sm font-medium">{value}</dd></div>)}</dl></section>
  </>
}

export function StrategiesPage() {
  const navigate = useNavigate()
  const selected = useBacktestDraftStore(state => state.strategy)
  const setStrategy = useBacktestDraftStore(state => state.setStrategy)
  return <>
    <PageHeading title="Strategies" description="Choose an approach, then explore its assumptions in your workspace." />
    <p className="mb-6 max-w-2xl text-sm leading-6 text-muted-foreground">These strategies are available to select for a draft. Simulation and strategy parameters are still in development.</p>
    <div className="divide-y border-y">
      {strategies.map(strategy => <section key={strategy.id} aria-labelledby={`strategy-${strategy.id}`} className="flex flex-col gap-5 py-7 sm:flex-row sm:items-center sm:justify-between sm:gap-8"><div className="min-w-0"><div className="flex flex-wrap items-center gap-3"><h2 id={`strategy-${strategy.id}`} className="text-base font-semibold">{strategy.name}</h2>{selected === strategy.id && <span className="inline-flex items-center gap-1 text-xs font-medium text-primary"><Check className="size-3.5" aria-hidden="true" />Current draft</span>}</div><p className="mt-2 max-w-xl text-sm leading-6 text-muted-foreground">{strategy.description}</p><p className="mt-3 text-xs text-muted-foreground">{strategy.approach}</p></div><Button variant="outline" className="self-start sm:self-center" aria-label={`${selected === strategy.id ? 'Open draft for' : 'Use'} ${strategy.name}`} onClick={() => { setStrategy(strategy.id); navigate('/workspace') }}>{selected === strategy.id ? 'Open draft' : 'Use strategy'}<ArrowRight aria-hidden="true" /></Button></section>)}
    </div>
    <p className="mt-6 text-xs leading-5 text-muted-foreground">Historical performance will be shown only after a backtest has actually been run.</p>
  </>
}

export function BacktestsPage() {
  return <>
    <PageHeading title="Backtests" description="Your experiments, their progress, and the results worth looking closer at." />
    <section aria-labelledby="backtests-empty" className="rounded-xl border px-6 py-12 sm:py-16"><div className="mx-auto max-w-md text-center"><History className="mx-auto mb-5 size-9 text-muted-foreground" strokeWidth={1.5} aria-hidden="true" /><h2 id="backtests-empty" className="text-xl font-semibold">The first experiment is still ahead.</h2><p className="mt-3 text-sm leading-6 text-muted-foreground">Backtest execution is being built. For now, prepare a strategy draft. Once runs are available, you’ll find their status and results here.</p><Button className="mt-6" asChild><Link to="/workspace">Prepare a strategy draft<ArrowRight aria-hidden="true" /></Link></Button></div></section>
    <section className="mt-10 max-w-2xl" aria-labelledby="backtest-results"><h2 id="backtest-results" className="text-base font-semibold">What a completed run will show</h2><ul className="mt-4 divide-y border-y text-sm">{['Performance against buy and hold, after trading costs', 'Individual trades and the drawdowns between them', 'The dataset, strategy, and settings used for the experiment'].map(text => <li key={text} className="py-4 leading-6 text-muted-foreground">{text}</li>)}</ul></section>
  </>
}

export function AccountPage() {
  const user = useAuth(state => state.user)
  if (!user) return null
  const created = new Date(user.createdAt)
  const joined = Number.isNaN(created.getTime()) ? 'Unavailable' : new Intl.DateTimeFormat('en', { dateStyle: 'long' }).format(created)
  return <div className="max-w-3xl">
    <PageHeading title="Your account" description="Your profile and the details that keep your research space yours." />
    <section aria-labelledby="profile-title"><h2 id="profile-title" className="text-base font-semibold">Profile</h2><dl className="mt-4 divide-y border-y">{[['Display name', user.displayName], ['Email address', user.email], ['Joined', joined]].map(([label, value]) => <div key={label} className="grid gap-2 py-5 sm:grid-cols-[160px_1fr]"><dt className="text-sm text-muted-foreground">{label}</dt><dd className="min-w-0 break-words text-sm font-medium">{value}{label === 'Email address' && <span className="ml-3 inline-flex items-center gap-1 text-xs font-medium text-primary"><Check className="size-3" aria-hidden="true" />Verified</span>}</dd></div>)}</dl></section>
    <section className="mt-10" aria-labelledby="account-security"><h2 id="account-security" className="flex items-center gap-2 text-base font-semibold"><ShieldCheck className="size-[18px] text-primary" aria-hidden="true" />Sign-in and security</h2><dl className="mt-4 divide-y border-y"><div className="grid gap-2 py-5 sm:grid-cols-[160px_1fr]"><dt className="text-sm text-muted-foreground">Sign-in method</dt><dd className="text-sm">Email and password</dd></div><div className="grid gap-2 py-5 sm:grid-cols-[160px_1fr]"><dt className="text-sm text-muted-foreground">Session</dt><dd className="max-w-lg text-sm leading-6">You’ll be asked to sign in again after 30 minutes without activity, or 8 hours after signing in.</dd></div></dl><p className="mt-5 text-sm leading-6 text-muted-foreground">Using a shared computer? Sign out from the sidebar when you’re done. Profile editing and password changes are not available yet.</p></section>
  </div>
}
