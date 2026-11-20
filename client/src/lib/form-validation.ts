export const fieldIds: Record<string, string> = {
  datasetId: 'dataset', strategy: 'strategy', rangeStart: 'range-start', rangeEnd: 'range-end',
  initialCapital: 'capital', allocationPercent: 'allocation', feeBps: 'fee-bps', slippageBps: 'slippage-bps',
  'parameters.fastPeriod': 'fast-period', 'parameters.slowPeriod': 'slow-period',
  'parameters.period': 'rsi-period', 'parameters.lowerThreshold': 'rsi-lower', 'parameters.upperThreshold': 'rsi-upper',
  parameters: 'strategy', name: 'preset-name', title: 'experiment-name', notes: 'experiment-notes', tags: 'experiment-tags',
  evaluationGoal: 'evaluation-goal',
}
export const fieldLabels: Record<string, string> = {
  datasetId: 'Historical dataset', strategy: 'Strategy', rangeStart: 'Start date', rangeEnd: 'End date',
  initialCapital: 'Starting capital', allocationPercent: 'Available cash per entry', feeBps: 'Fee', slippageBps: 'Slippage',
  'parameters.fastPeriod': 'Fast SMA period', 'parameters.slowPeriod': 'Slow SMA period', 'parameters.period': 'RSI period',
  'parameters.lowerThreshold': 'Lower threshold', 'parameters.upperThreshold': 'Upper threshold', parameters: 'Strategy parameters',
  name: 'Preset name', title: 'Experiment name', notes: 'Research notes', tags: 'Tags', evaluationGoal: 'Comparison focus',
}

export function focusInvalidField(fields: Record<string, string>, targets = fieldIds) {
  const id = Object.keys(fields).map(field => targets[field]).find(id => id && document.getElementById(id))
  if (id) requestAnimationFrame(() => document.getElementById(id)?.focus())
}

