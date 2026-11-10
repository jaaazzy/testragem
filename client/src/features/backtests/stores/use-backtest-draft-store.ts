import { create } from 'zustand'

import type { BacktestDraft, StrategyId } from '../types/backtest'

interface BacktestDraftState extends BacktestDraft {
  setStrategy: (strategy: StrategyId) => void
  setInitialCapital: (capital: number) => void
  reset: () => void
}

const defaults: BacktestDraft = { strategy: 'ma-crossover', initialCapital: 10000 }

export const useBacktestDraftStore = create<BacktestDraftState>()((set) => ({
  ...defaults,
  setStrategy: (strategy) => set({ strategy }),
  setInitialCapital: (initialCapital) => {
    if (Number.isFinite(initialCapital) && initialCapital > 0) set({ initialCapital })
  },
  reset: () => set(defaults),
}))

