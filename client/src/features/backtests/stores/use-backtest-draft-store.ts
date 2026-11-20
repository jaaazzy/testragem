import { create } from 'zustand'

import type { BacktestDraft, StrategyId } from '../types/backtest'

interface BacktestDraftState extends BacktestDraft {
  presetId: string
  presetVersion: number
  setStrategy: (strategy: StrategyId) => void
  setInitialCapital: (capital: number) => void
  reset: () => void
  update: (settings: Partial<BacktestDraft>) => void
}

const defaults: BacktestDraft = { strategy: 'ma-crossover', initialCapital: 10000,datasetId:'',rangeStart:'',rangeEnd:'',feeBps:0,slippageBps:0,allocationPercent:100,fastPeriod:10,slowPeriod:30,period:14,lowerThreshold:30,upperThreshold:70 }

export const useBacktestDraftStore = create<BacktestDraftState>()((set) => ({
  ...defaults,
  presetId: '', presetVersion: 0,
  setStrategy: (strategy) => set({ strategy }),
  setInitialCapital: (initialCapital) => {
    if (Number.isFinite(initialCapital) && initialCapital > 0) set({ initialCapital })
  },
  reset: () => set({...defaults, presetId:'', presetVersion:0}),
  update: (settings) => set(settings),
}))

