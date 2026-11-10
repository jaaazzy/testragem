import type { ReactNode } from 'react'

/** Only the changing content remounts; the workspace navigation stays in place. */
export function PageTransition({ children, routeKey, animate = true }: { children: ReactNode; routeKey: string; animate?: boolean }) {
  return <div key={routeKey} data-slot="page-transition" className={animate ? 'page-transition' : undefined}>{children}</div>
}
