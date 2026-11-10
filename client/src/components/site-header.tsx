import { Link, NavLink } from 'react-router'
import { ArrowRight, UserRound } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/features/auth/store'
import { ThemeToggle } from './theme-toggle'
import testragemIcon from '../assets/testragem-icon.svg'

export function SiteHeader() {
  const { user, loading } = useAuth()
  return <header className="site-header border-b bg-background">
    <div className="site-header-inner mx-auto max-w-[1280px] px-6 py-4 md:px-10">
      <Link to="/" className="site-brand flex w-fit items-center gap-2 font-display text-xl font-bold tracking-tight" aria-label="Testragem home"><img src={testragemIcon} className="brand-mark size-9 object-contain" width="36" height="36" alt="" />testragem</Link>
      <nav aria-label="Main navigation" className="site-navigation flex items-center gap-3 sm:gap-6">{[['/about','About'],['/protocol','How it works']].map(([to,label]) => <NavLink key={to} to={to} className={({isActive}) => `inline-flex min-h-11 items-center whitespace-nowrap text-sm font-medium underline-offset-8 ${isActive ? 'text-foreground underline decoration-primary decoration-2' : 'text-muted-foreground hover:text-foreground'}`}>{label}</NavLink>)}</nav>
      <ThemeToggle className="site-appearance" />
      <nav aria-label="Account navigation" className="site-account flex items-center justify-end gap-1 sm:gap-2">
        {user ? <><Button variant="ghost" size="icon" className="hidden sm:inline-flex" asChild><Link to="/workspace/account" aria-label="Your account" title={user.displayName}><UserRound aria-hidden="true" /></Link></Button><Button size="sm" asChild><Link to="/workspace"><span className="sm:hidden">Workspace</span><span className="hidden sm:inline">Open workspace</span><ArrowRight className="hidden sm:block" aria-hidden="true" /></Link></Button></> : loading ? <Skeleton className="h-11 w-28" /> : <><Button variant="ghost" size="sm" className="px-2" asChild><Link to="/login">Log in</Link></Button><Button size="sm" asChild><Link to="/signup">Sign up</Link></Button></>}
      </nav>
    </div>
  </header>
}
