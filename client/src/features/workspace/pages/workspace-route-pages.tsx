import { lazy } from 'react'

export const BacktestWorkspacePage = lazy(() => import('@/features/backtests/pages/backtest-workspace-page').then(module => ({ default: module.BacktestWorkspacePage })))
export const AccountPage = lazy(() => import('./workspace-pages').then(module => ({ default: module.AccountPage })))
export const BacktestsPage = lazy(() => import('./workspace-pages').then(module => ({ default: module.BacktestsPage })))
export const DatasetsPage = lazy(() => import('./workspace-pages').then(module => ({ default: module.DatasetsPage })))
export const StrategiesPage = lazy(() => import('./workspace-pages').then(module => ({ default: module.StrategiesPage })))
