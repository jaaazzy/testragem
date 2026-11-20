import { useNavigate } from 'react-router'
import { PreferencesPanel } from '@/features/backtests/components/personal-workspace'
import type { ReactNode } from 'react'
import { ArrowRight, Check, ShieldCheck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/store'
import { strategies } from '@/features/backtests/strategies'
import { useBacktestDraftStore } from '@/features/backtests/stores/use-backtest-draft-store'

export function PageHeading({ title, description, action }: { title: string; description: string; action?: ReactNode }) {
  return <div className="mb-8 flex flex-wrap items-start justify-between gap-4 border-b pb-7"><div className="min-w-0 flex-1 basis-60"><h1 className="text-[1.75rem] leading-tight font-semibold tracking-tight sm:text-[2rem]">{title}</h1><p className="mt-2 max-w-[60ch] text-sm leading-6 text-muted-foreground">{description}</p></div>{action&&<div className="pt-1">{action}</div>}</div>
}

export function StrategiesPage() {
  const navigate = useNavigate()
  const selected = useBacktestDraftStore(state => state.strategy)
  const setStrategy = useBacktestDraftStore(state => state.setStrategy)
  return <>
    <PageHeading title="Strategies" description="Choose an approach, then explore its assumptions in your workspace." />
    <p className="mb-6 max-w-2xl text-sm leading-6 text-muted-foreground">Prepare the parameters for a strategy in your draft. Its run availability is checked with the server.</p>
    <div className="divide-y border-y">
      {strategies.map(strategy => <section key={strategy.id} aria-labelledby={`strategy-${strategy.id}`} className="flex flex-col gap-5 py-7 sm:flex-row sm:items-center sm:justify-between sm:gap-8"><div className="min-w-0"><div className="flex flex-wrap items-center gap-3"><h2 id={`strategy-${strategy.id}`} className="text-base font-semibold">{strategy.name}</h2>{selected === strategy.id && <span className="inline-flex items-center gap-1 text-xs font-medium text-primary"><Check className="size-3.5" aria-hidden="true" />Current draft</span>}</div><p className="mt-2 max-w-xl text-sm leading-6 text-muted-foreground">{strategy.description}</p><p className="mt-3 text-xs text-muted-foreground">{strategy.approach}</p></div><Button variant="outline" className="self-start sm:self-center" aria-label={`${selected === strategy.id ? 'Open draft for' : 'Use'} ${strategy.name}`} onClick={() => { setStrategy(strategy.id); navigate('/workspace') }}>{selected === strategy.id ? 'Open draft' : 'Use strategy'}<ArrowRight aria-hidden="true" /></Button></section>)}
    </div>
    <p className="mt-6 text-xs leading-5 text-muted-foreground">Historical performance will be shown only after a backtest has actually been run.</p>
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
    <PreferencesPanel key={user.id} />
  </div>
}
