import type { StrategyId } from './types/backtest'

export const strategies: { id: StrategyId; name: string; description: string; approach: string }[] = [
  { id: 'ma-crossover', name: 'Moving-average crossover', description: 'Explore a trend-following strategy based on moving-average crossovers.', approach: 'Trend following' },
  { id: 'rsi', name: 'RSI strategy', description: 'Explore a momentum strategy based on the relative strength index.', approach: 'Momentum' },
  { id: 'buy-and-hold', name: 'Buy and hold', description: 'Hold Bitcoin throughout the study as a simple baseline for comparison.', approach: 'Benchmark' },
]
