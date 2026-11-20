export type StrategyId = 'buy-and-hold' | 'ma-crossover' | 'rsi'

export interface BacktestDraft {
  strategy: StrategyId
  initialCapital: number
  datasetId: string
  rangeStart: string
  rangeEnd: string
  feeBps: number
  slippageBps: number
  allocationPercent: number
  fastPeriod: number
  slowPeriod: number
  period: number
  lowerThreshold: number
  upperThreshold: number
}

