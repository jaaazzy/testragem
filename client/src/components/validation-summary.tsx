import { ErrorNotice } from './error-notice'

import { fieldIds, fieldLabels } from '@/lib/form-validation'

export function ValidationSummary({ fields, targets = fieldIds }: { fields: Record<string, string>; targets?: Record<string, string> }) {
  if (!Object.keys(fields).length) return null
  return <ErrorNotice title="Review your settings" description="Correct these fields, then submit again. Your entries have been kept.">
    <ul className="space-y-1">{Object.entries(fields).map(([field, message]) => <li key={field}>
      {targets[field] ? <button type="button" className="min-h-9 max-w-full break-words text-left text-sm leading-5 underline decoration-border underline-offset-4 hover:decoration-foreground focus-visible:rounded focus-visible:outline-2 focus-visible:outline-ring" onClick={() => document.getElementById(targets[field])?.focus()}>{fieldLabels[field] ?? 'Settings'}: {message}</button>
        : <span className="break-words text-sm leading-5">{fieldLabels[field] ?? 'Settings'}: {message}</span>}
    </li>)}</ul>
  </ErrorNotice>
}
