import { Link, createSearchParams, useSearchParams } from 'react-router'
import { safeReturnTo } from '@/app/paths'
import { useEffect } from 'react'
import { ArrowLeft, ArrowUpRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardHeader, CardContent, CardFooter } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { HeroChessBackdrop } from '@/features/home/components/hero-chess-backdrop'
import { AuthForm, type AuthMode } from '../components/auth-form'
import { VerificationPanel } from '../components/verification-panel'
import { ThemeToggle } from '@/components/theme-toggle'

const prices = [35, 40, 37, 47, 43, 52, 56, 50, 61, 66, 59, 69, 64, 74, 79, 73, 83, 78]

export function AuthPage({ mode }: { mode: AuthMode | 'verify' }) {
  const [searchParams] = useSearchParams()
  const nextSearch = searchParams.has('next') ? `?${createSearchParams({ next: safeReturnTo(searchParams.get('next')) })}` : ''
  const signup = mode === 'signup'
  const verification = mode === 'verify'
  useEffect(() => {
    window.scrollTo(0, 0)
    document.title = `${verification ? 'Verify email' : signup ? 'Sign up' : 'Log in'} | Testragem`
    return () => { document.title = 'Testragem' }
  }, [signup, verification])

  return (
    <div className="min-h-screen bg-background">
      <a className="skip-link" href="#auth-content" onClick={event => { event.preventDefault(); const content = document.getElementById('auth-content'); content?.focus(); content?.scrollIntoView({ block: 'start' }) }}>Skip to form</a>
      <header className="mx-auto flex max-w-[1280px] items-center justify-between gap-3 px-6 py-5 md:px-10">
        <Button variant="ghost" size="sm" asChild><Link to="/"><ArrowLeft aria-hidden="true" /><span className="hidden sm:inline">Back to home</span><span className="sm:hidden">Home</span></Link></Button>
        <ThemeToggle />
      </header>
      <main id="auth-content" tabIndex={-1} className="mx-auto grid max-w-[1280px] gap-10 px-6 pb-10 md:px-10 lg:grid-cols-[1.05fr_1fr] lg:gap-16">
        <aside className="relative isolate hidden min-h-[660px] flex-col justify-between overflow-hidden rounded-2xl bg-muted p-10 lg:flex" aria-label="About Testragem">
          <HeroChessBackdrop />
          <div className="relative z-10"><h2 className="max-w-[370px] text-3xl leading-[1.25] font-medium tracking-[-.025em]">A calmer place<br />to study the market.</h2><p className="mt-4 max-w-[330px] text-sm leading-7 text-muted-foreground">Bring your questions. Explore crypto strategies, one thoughtful experiment at a time.</p></div>
          <svg viewBox="0 0 440 250" className="relative z-10 my-8 w-full" aria-hidden="true" focusable="false">
            {[55,105,155,205].map(y => <line key={y} x1="0" x2="440" y1={y} y2={y} stroke="var(--border)" />)}
            {prices.map((close,index) => { const open = prices[Math.max(0,index-1)] - (index === 0 ? 4 : 0); const y = 230 - Math.max(open,close) * 2; const height = Math.max(4,Math.abs(close-open)*2); const color = close >= open ? 'var(--color-positive)' : 'var(--color-negative)'; return <g key={index} fill={color} stroke={color}><line x1={12+index*24} x2={12+index*24} y1={y-10} y2={y+height+12} strokeWidth="1.2" /><rect x={8+index*24} y={y} width="8" height={height} rx="1" stroke="none" /></g> })}
          </svg>
          <div className="relative z-10"><Separator className="mb-5" /><p className="text-xs text-muted-foreground">Illustrative study / BTCUSDT / 1-hour candles</p><Button variant="link" className="mt-3 px-0" asChild><Link to="/workspace">Take a look around<ArrowUpRight aria-hidden="true" /></Link></Button></div>
        </aside>
        <div className="flex min-w-0 items-center justify-center py-6 sm:py-10 lg:py-8">
          <Card className="w-full max-w-[410px] gap-7 border-0 bg-transparent py-0">
            <CardHeader className="gap-3 px-0"><h1 className="text-[clamp(30px,4vw,36px)] leading-tight font-semibold tracking-[-.025em]">{verification ? 'Verify your email.' : signup ? 'Create your account.' : 'Welcome back.'}</h1><p className="text-sm leading-6 text-muted-foreground">{verification ? 'Confirm your address to finish setting up your account.' : signup ? 'A little space for your next trading idea.' : 'Make room for your next what-if.'}</p></CardHeader>
            <CardContent className="px-0">{mode === 'verify' ? <VerificationPanel /> : <AuthForm mode={mode} />}</CardContent>
            <CardFooter className="flex-col items-stretch gap-6 px-0"><Separator /><p className="text-center text-sm text-muted-foreground">{verification ? 'Already verified?' : signup ? 'Already have an account?' : 'New to Testragem?'}{' '}<Link to={(signup || verification ? '/login' : '/signup') + nextSearch} className="font-medium text-primary underline-offset-4 hover:underline">{signup || verification ? 'Log in' : 'Create an account'}</Link></p></CardFooter>
          </Card>
        </div>
      </main>
    </div>
  )
}
