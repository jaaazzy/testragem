import { lazy } from 'react'

export const ComparisonPage = lazy(() => import('@/features/backtests/pages/comparison-page').then(module => ({ default: module.ComparisonPage })))

export const BacktestWorkspacePage = lazy(() => import('@/features/backtests/pages/backtest-workspace-page').then(module => ({ default: module.BacktestWorkspacePage })))
export const AccountPage = lazy(() => import('./workspace-pages').then(module => ({ default: module.AccountPage })))
export const BacktestsPage = lazy(() => import('@/features/backtests/pages/research-pages').then(module => ({ default: module.RunHistoryPage })))
export const DatasetsPage = lazy(() => import('@/features/backtests/pages/research-pages').then(module => ({ default: module.DatasetBrowserPage })))
export const RunDetailPage = lazy(() => import('@/features/backtests/pages/research-pages').then(module => ({ default: module.RunDetailPage })))
export const StrategiesPage = lazy(() => import('./workspace-pages').then(module => ({ default: module.StrategiesPage })))
