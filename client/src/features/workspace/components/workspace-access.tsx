import { Link, Navigate, Outlet, createSearchParams, useLocation } from 'react-router'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/store'
import { AppShell } from './app-shell'
import { Suspense } from 'react'
import { WorkspacePageSkeleton, WorkspaceSkeleton } from './workspace-skeleton'

export function WorkspaceAccess() {
  const { user, loading, error, refresh } = useAuth()
  const location = useLocation()

  if (user) return <AppShell user={user}><Suspense fallback={<WorkspacePageSkeleton />}><Outlet /></Suspense></AppShell>
  if (error) return <main className="mx-auto max-w-lg px-6 py-20"><h1 className="text-2xl font-semibold">We couldn’t open your workspace.</h1><p role="alert" className="mt-4 text-sm leading-6 text-muted-foreground">{error}</p><div className="mt-6 flex gap-3"><Button onClick={() => void refresh()}>Try again</Button><Button variant="outline" asChild><Link to="/">Back to home</Link></Button></div></main>
  if (!loading) return <Navigate to={{ pathname: '/login', search: createSearchParams({ next: location.pathname + location.search + location.hash }).toString() }} replace />
  return <WorkspaceSkeleton />
}
