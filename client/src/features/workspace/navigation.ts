import { FlaskConical, Database, Layers3, History, UserRound } from 'lucide-react'

export const workspaceSections = [
  { id: 'workspace', to: '/workspace', label: 'Workspace', icon: FlaskConical },
  { id: 'datasets', to: '/workspace/datasets', label: 'Datasets', icon: Database },
  { id: 'strategies', to: '/workspace/strategies', label: 'Strategies', icon: Layers3 },
  { id: 'backtests', to: '/workspace/backtests', label: 'Backtests', icon: History },
  { id: 'account', to: '/workspace/account', label: 'Account', icon: UserRound },
] as const

export type WorkspaceSection = typeof workspaceSections[number]['id']

