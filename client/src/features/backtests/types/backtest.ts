export type StrategyId = 'buy-and-hold' | 'ma-crossover' | 'rsi'

export interface BacktestDraft {
  strategy: StrategyId
  initialCapital: number
}

