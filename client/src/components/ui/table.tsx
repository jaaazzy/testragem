import type { ComponentProps } from 'react'
import { cn } from '@/lib/utils'

// Text columns align left by default. Apply text-right to BOTH the heading and
// cells of a numeric column so its label and values share the same edge.
export function Table({ className, ...props }: ComponentProps<'table'>) {
  return <div data-slot="table-container" className="relative w-full overflow-x-auto">
    <table data-slot="table" className={cn('w-full caption-bottom text-sm', className)} {...props} />
  </div>
}

export function TableHeader({ className, ...props }: ComponentProps<'thead'>) {
  return <thead data-slot="table-header" className={cn('border-b border-border', className)} {...props} />
}

export function TableBody({ className, ...props }: ComponentProps<'tbody'>) {
  return <tbody data-slot="table-body" className={cn('[&_tr:last-child]:border-0', className)} {...props} />
}

export function TableFooter({ className, ...props }: ComponentProps<'tfoot'>) {
  return <tfoot data-slot="table-footer" className={cn('border-t border-border bg-muted/50 font-medium [&>tr]:last:border-b-0', className)} {...props} />
}

export function TableRow({ className, ...props }: ComponentProps<'tr'>) {
  return <tr data-slot="table-row" className={cn('border-b border-border', className)} {...props} />
}

export function TableHead({ className, scope = 'col', ...props }: ComponentProps<'th'>) {
  return <th data-slot="table-head" scope={scope} className={cn('px-3 py-3 text-left align-middle font-medium whitespace-nowrap text-muted-foreground first:pl-0 last:pr-0', className)} {...props} />
}

export function TableCell({ className, ...props }: ComponentProps<'td'>) {
  return <td data-slot="table-cell" className={cn('px-3 py-3 text-left align-middle font-medium whitespace-nowrap first:pl-0 last:pr-0', className)} {...props} />
}

export function TableCaption({ className, ...props }: ComponentProps<'caption'>) {
  return <caption data-slot="table-caption" className={cn('mt-4 text-sm text-muted-foreground', className)} {...props} />
}
