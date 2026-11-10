import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, NavLink, useLocation, useMatches, useNavigate } from 'react-router'
import { ArrowUpRight, ChevronRight, LogOut, Menu, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/store'
import { authErrorMessage, type User } from '@/features/auth/api'
import { cn } from '@/lib/utils'
import testragemIcon from '@/assets/testragem-icon.svg'
import { workspaceSections } from '../navigation'
import { ThemeToggle } from '@/components/theme-toggle'
import { PageTransition } from '@/components/page-transition'

function Sidebar({ user, onNavigate }: { user: User; onNavigate?: () => void }) {
  const navigate = useNavigate()
  const signOut = useAuth(state => state.signOut)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  async function logout() {
    if (pending) return
    setPending(true); setError('')
    try { await signOut(); onNavigate?.(); navigate('/login', { replace: true }) }
    catch (failure) { setError(authErrorMessage(failure)) }
    finally { setPending(false) }
  }
  const initials = user.displayName.trim().split(/\s+/).slice(0, 2).map(name => [...name][0]).join('').toUpperCase()
  return (
    <div className="workspace-sidebar flex h-full min-h-0 flex-col overflow-y-auto overscroll-contain px-4 pb-5 pt-6">
      <Link to="/workspace" onClick={onNavigate} aria-label="Testragem workspace" className="mb-9 flex w-fit shrink-0 items-center gap-2 px-3 font-display text-xl font-bold tracking-tight">
        <img src={testragemIcon} width="32" height="32" alt="" className="brand-mark size-8" />testragem
      </Link>
      <p className="mb-2 px-3 text-xs font-medium text-muted-foreground">Research</p>
      <nav aria-label="Workspace navigation" className="workspace-navigation shrink-0 space-y-1">
        {workspaceSections.map(({ id, to, label, icon: Icon }) => <NavLink key={id} to={to} end={id === 'workspace'} onClick={onNavigate} className={({ isActive }) => cn('flex min-h-11 items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors', isActive ? 'bg-secondary text-secondary-foreground' : 'text-muted-foreground hover:bg-secondary/60 hover:text-foreground', id === 'account' && 'mt-6')}><Icon className="size-[18px] shrink-0" aria-hidden="true" />{label}</NavLink>)}
      </nav>
      <div className="mt-auto shrink-0 pt-8">
        <div className="mb-3 border-b pb-3">
          <Link to="/protocol" onClick={onNavigate} className="flex min-h-11 items-center justify-between gap-2 rounded-lg px-3 text-xs font-medium text-muted-foreground hover:bg-secondary/60 hover:text-foreground">How Testragem works<ArrowUpRight className="size-3.5" aria-hidden="true" /></Link>
        </div>
        <Link to="/workspace/account" onClick={onNavigate} className="flex min-w-0 items-center gap-3 rounded-lg px-3 py-3 hover:bg-secondary/60" aria-label="View your account">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-semibold text-primary" aria-hidden="true">{initials}</span>
          <span className="min-w-0"><span className="block truncate text-sm font-medium">{user.displayName}</span><span className="mt-0.5 block truncate text-xs text-muted-foreground" title={user.email}>{user.email}</span></span>
        </Link>
        <Button variant="ghost" disabled={pending} onClick={logout} className="mt-1 w-full justify-start px-3 text-muted-foreground"><LogOut aria-hidden="true" />{pending ? 'Signing out…' : 'Sign out'}</Button>
        {error && <p role="alert" className="px-3 pt-2 text-xs leading-5 text-destructive">{error}</p>}
      </div>
    </div>
  )
}

export function AppShell({ user, children }: { user: User; children: ReactNode }) {
  const location = useLocation()
  const matches = useMatches()
  const drawer = useRef<HTMLDialogElement>(null)
  const menuButton = useRef<HTMLButtonElement>(null)
  const openedRoute = useRef('')
  const main = useRef<HTMLElement>(null)
  const handle = matches.at(-1)?.handle as { title?: string } | undefined
  const label = handle?.title ?? 'Workspace'
  const error = useAuth(state => state.error)
  const refresh = useAuth(state => state.refresh)

  useEffect(() => {
    document.title = `${label} | Testragem`
    drawer.current?.close()
    window.scrollTo(0, 0)
    main.current?.focus({ preventScroll: true })
    return () => { document.title = 'Testragem' }
  }, [label, location.pathname])

  useEffect(() => {
    const desktop = window.matchMedia('(min-width: 1024px)')
    const closeOnDesktop = () => { if (desktop.matches) drawer.current?.close() }
    desktop.addEventListener('change', closeOnDesktop)
    return () => desktop.removeEventListener('change', closeOnDesktop)
  }, [])

  return (
    <div className="workspace-app min-h-dvh bg-background">
      <a className="skip-link" href="#workspace-content" onClick={event => { event.preventDefault(); main.current?.focus() }}>Skip to workspace content</a>
      <aside className="fixed inset-y-0 left-0 hidden w-60 border-r bg-muted/60 lg:block"><Sidebar user={user} /></aside>
      <dialog ref={drawer} id="workspace-menu" aria-label="Workspace menu" className="workspace-drawer fixed inset-y-0 left-0 m-0 h-dvh max-h-dvh w-72 max-w-[calc(100%-3rem)] border-0 border-r bg-background p-0 text-foreground" onClose={() => {
        if (location.key === openedRoute.current && !window.matchMedia('(min-width: 1024px)').matches) menuButton.current?.focus()
        else main.current?.focus({ preventScroll: true })
      }} onClick={event => {
        if (event.target === drawer.current && event.clientX > drawer.current.getBoundingClientRect().right) drawer.current.close()
      }}>
        <div className="h-full"><Button variant="ghost" size="icon" className="absolute right-3 top-5" aria-label="Close navigation" onClick={() => drawer.current?.close()}><X aria-hidden="true" /></Button><Sidebar user={user} onNavigate={() => drawer.current?.close()} /></div>
      </dialog>
      <div className="lg:pl-60">
        <header className="workspace-toolbar sticky top-0 z-10 flex min-h-[76px] items-center justify-between gap-4 border-b px-5 sm:px-8">
          <div className="flex min-w-0 items-center gap-3">
            <Button ref={menuButton} variant="ghost" size="icon" className="-ml-2 lg:hidden" aria-label="Open navigation" aria-haspopup="dialog" aria-controls="workspace-menu" onClick={() => { openedRoute.current = location.key; drawer.current?.showModal() }}><Menu aria-hidden="true" /></Button>
            <nav aria-label="Breadcrumb" className="flex min-w-0 items-center gap-3 text-sm">{location.pathname !== '/workspace' && <><Link to="/workspace" className="hidden min-h-11 items-center text-muted-foreground hover:text-foreground lg:inline-flex">Workspace</Link><ChevronRight className="hidden size-3.5 shrink-0 text-muted-foreground lg:block" aria-hidden="true" /></>}<span aria-current="page" className="truncate font-medium">{label}</span></nav>
          </div>
          <ThemeToggle />
        </header>
        {error && <div role="status" className="flex flex-wrap items-center gap-x-4 gap-y-1 border-b bg-muted px-5 py-2 text-sm sm:px-8"><p>{error}</p><Button variant="link" size="sm" onClick={() => void refresh()}>Try again</Button></div>}
        <main id="workspace-content" ref={main} tabIndex={-1} className="mx-auto max-w-[1240px] px-5 pb-12 pt-8 outline-none sm:px-8 sm:pt-10 lg:px-10"><PageTransition routeKey={location.pathname}>{children}</PageTransition></main>
      </div>
    </div>
  )
}
