import { useEffect } from 'react'
import { Outlet, useLocation } from 'react-router'
import { PageTransition } from '@/components/page-transition'
import { useAuth } from '../features/auth/store'

export default function App() {
  const { pathname } = useLocation()
  const workspace = pathname === '/workspace' || pathname.startsWith('/workspace/')
  useEffect(() => {
    const refresh = () => { void useAuth.getState().refresh() }
    refresh()
    window.addEventListener('focus', refresh)
    return () => window.removeEventListener('focus', refresh)
  }, [])
  return <PageTransition routeKey={workspace ? 'workspace' : pathname} animate={!workspace}><Outlet /></PageTransition>
}

