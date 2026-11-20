import type { Configuration } from './api'
import type { BacktestDraft } from './types/backtest'

export type Preset = { id: string; version: number; name: string; configuration: Configuration; createdAt: string }
export type Preferences = { initialCapital: number; feeBps: number; slippageBps: number; allocationPercent: number; evaluationGoal: 'return' | 'drawdown' | 'costs' }
export type Annotation = { title: string; notes: string; tags: string[] }
export type RunAnnotation = { id: string; annotation: Annotation }
export const emptyAnnotation: Annotation = { title: '', notes: '', tags: [] }
export const emptyId = '00000000-0000-0000-0000-000000000000'

export function configurationFromDraft(draft: BacktestDraft): Configuration {
  return {
    datasetId: draft.datasetId, strategy: draft.strategy,
    rangeStart: new Date(draft.rangeStart + 'Z').toISOString(),
    rangeEnd: new Date(draft.rangeEnd + 'Z').toISOString(),
    initialCapital: draft.initialCapital, feeBps: draft.feeBps,
    slippageBps: draft.slippageBps, allocationPercent: draft.allocationPercent,
    parameters: draft.strategy === 'ma-crossover'
      ? { fastPeriod: draft.fastPeriod, slowPeriod: draft.slowPeriod }
      : draft.strategy === 'rsi'
        ? { period: draft.period, lowerThreshold: draft.lowerThreshold, upperThreshold: draft.upperThreshold }
        : {},
  }
}

export function draftFromConfiguration(config: Configuration): Partial<BacktestDraft> {
  return {
    strategy: config.strategy as BacktestDraft['strategy'], datasetId: config.datasetId,
    rangeStart: config.rangeStart.slice(0, 16), rangeEnd: config.rangeEnd.slice(0, 16),
    initialCapital: config.initialCapital, feeBps: config.feeBps,
    slippageBps: config.slippageBps, allocationPercent: config.allocationPercent,
    ...config.parameters,
  }
}
