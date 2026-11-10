import { useEffect } from 'react'
import { Link, isRouteErrorResponse, useRouteError } from 'react-router'
import { Button } from '@/components/ui/button'

export function NotFoundPage({ workspace = false }: { workspace?: boolean }) {
  useEffect(() => { document.title = 'Page not found | Testragem' }, [])
  const content = <><h1 className="text-3xl font-semibold tracking-tight">Page not found</h1><p className="mt-4 max-w-lg text-sm leading-6 text-muted-foreground">This page doesn’t exist. Check the address or return to {workspace ? 'your workspace' : 'the home page'}.</p><Button className="mt-6" asChild><Link to={workspace ? '/workspace' : '/'}>{workspace ? 'Back to workspace' : 'Back to home'}</Link></Button></>
  return workspace ? <section>{content}</section> : <main className="mx-auto max-w-3xl px-6 py-20">{content}</main>
}

export function RouteErrorPage() {
  const error = useRouteError()
  if (isRouteErrorResponse(error) && error.status === 404) return <NotFoundPage />
  return <main className="mx-auto max-w-3xl px-6 py-20"><h1 className="text-3xl font-semibold tracking-tight">We couldn’t open this page.</h1><p role="alert" className="mt-4 max-w-lg text-sm leading-6 text-muted-foreground">Something went wrong loading the page. Try again, or return to the home page.</p><div className="mt-6 flex flex-wrap gap-3"><Button onClick={() => window.location.reload()}>Reload page</Button><Button variant="outline" asChild><Link to="/">Back to home</Link></Button></div></main>
}
