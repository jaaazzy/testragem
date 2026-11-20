import type { RouteObject } from 'react-router'
import App from './App'
import { HomePage } from '@/features/home/pages/home-page'
import { AuthPage } from '@/features/auth/pages/auth-page'
import { WorkspaceAccess } from '@/features/workspace/components/workspace-access'
import { NotFoundPage, RouteErrorPage } from './route-pages'
import { AccountPage, BacktestsPage, BacktestWorkspacePage, DatasetsPage, StrategiesPage, RunDetailPage, ComparisonPage } from '@/features/workspace/pages/workspace-route-pages'

/** One route tree for production and memory-router tests. The parent owns auth/layout. */
export const appRoutes: RouteObject[] = [{
  path: '/',
  element: <App />,
  errorElement: <RouteErrorPage />,
  children: [
    { index: true, element: <HomePage /> },
    { path: 'about', element: <HomePage section="about" /> },
    { path: 'protocol', element: <HomePage section="protocol" /> },
    { path: 'login', element: <AuthPage key="login" mode="login" /> },
    { path: 'signup', element: <AuthPage key="signup" mode="signup" /> },
    { path: 'verify-email', element: <AuthPage key="verify" mode="verify" /> },
    {
      path: 'workspace',
      element: <WorkspaceAccess />,
      children: [
        { index: true, element: <BacktestWorkspacePage />, handle: { title: 'Workspace' } },
        { path: 'datasets', element: <DatasetsPage />, handle: { title: 'Datasets' } },
        { path: 'strategies', element: <StrategiesPage />, handle: { title: 'Strategies' } },
        { path: 'backtests', element: <BacktestsPage />, handle: { title: 'Backtests' } },
        { path: 'compare', element: <ComparisonPage />, handle: { title: 'Compare experiments' } },
        { path: 'backtests/:id', element: <RunDetailPage />, handle: { title: 'Backtest result' } },
        { path: 'account', element: <AccountPage />, handle: { title: 'Account' } },
        { path: '*', element: <NotFoundPage workspace />, handle: { title: 'Page not found' } },
      ],
    },
    { path: '*', element: <NotFoundPage /> },
  ],
}]
