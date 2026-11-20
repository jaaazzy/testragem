import type { ReactNode } from 'react'
import { CircleAlert } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

/** One presentation for recoverable failures. Keep entered data visible beside it. */
export function ErrorNotice({ title, description, action, children, className }: {
  title: string
  description: string
  action?: { label: string; onClick: () => void; disabled?: boolean }
  children?: ReactNode
  className?: string
}) {
  return <Alert variant="destructive" className={className}>
    <CircleAlert aria-hidden="true" />
    <AlertTitle>{title}</AlertTitle>
    <AlertDescription>{description}</AlertDescription>
    {(children || action) && <div className="col-start-2 min-w-0 space-y-2 pt-2">
      {children}
      {action && <Button type="button" variant="outline" size="sm" disabled={action.disabled} onClick={action.onClick}>{action.label}</Button>}
    </div>}
  </Alert>
}

export function FieldError({ id, message, className }: { id: string; message?: string; className?: string }) {
  return message ? <p id={id} className={cn('flex items-start gap-1.5 break-words text-sm leading-5 text-destructive', className)}><CircleAlert className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" /><span>{message}</span></p> : null
}
