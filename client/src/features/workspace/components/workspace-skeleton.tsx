import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

export function WorkspacePageSkeleton() {
  return <div role="status" aria-label="Loading workspace page" aria-busy="true">
    <span className="sr-only">Loading workspace page…</span>
    <div aria-hidden="true">
      <Skeleton className="h-9 w-48 max-w-full" />
      <Skeleton className="mt-3 h-5 w-80 max-w-full" />
      <div className="mt-8 grid gap-7 xl:grid-cols-[1.15fr_1fr]">
        <Card><CardHeader><Skeleton className="h-6 w-44 max-w-full" /><Skeleton className="mt-2 h-4 w-56 max-w-full" /></CardHeader><CardContent className="space-y-6"><Skeleton className="h-11 w-full" /><Skeleton className="h-16 w-full" /><div className="flex justify-between gap-4"><Skeleton className="h-11 w-28" /><Skeleton className="h-11 w-28" /></div></CardContent></Card>
        <Card><CardHeader><Skeleton className="h-6 w-36" /></CardHeader><CardContent className="space-y-6">{[1, 2, 3].map(row => <Skeleton key={row} className="h-6 w-full" />)}</CardContent></Card>
      </div>
    </div>
  </div>
}

export function WorkspaceSkeleton() {
  return <div className="workspace-app min-h-dvh">
    <div aria-hidden="true" className="fixed inset-y-0 hidden w-60 border-r bg-muted/60 px-7 py-8 lg:block"><Skeleton className="h-8 w-36" /><div className="mt-12 space-y-4">{[1, 2, 3, 4, 5].map(row => <Skeleton key={row} className="h-11 w-full" />)}</div></div>
    <div className="lg:pl-60"><div aria-hidden="true" className="flex h-[76px] items-center justify-between gap-5 border-b px-5 sm:px-8"><Skeleton className="h-5 w-32" /><Skeleton className="h-11 w-28 rounded-full" /></div><main className="mx-auto max-w-[1240px] px-5 pb-12 pt-8 sm:px-8 sm:pt-10 lg:px-10"><WorkspacePageSkeleton /></main></div>
  </div>
}
