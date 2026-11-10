import type { ComponentProps } from 'react'
import { cn } from '@/lib/utils'
export function Table({className,...props}:ComponentProps<'table'>){return <div className="relative w-full overflow-x-auto"><table data-slot="table" className={cn('w-full text-sm',className)} {...props}/></div>}
export function TableBody(props:ComponentProps<'tbody'>){return <tbody data-slot="table-body" {...props}/>}
export function TableRow({className,...props}:ComponentProps<'tr'>){return <tr data-slot="table-row" className={cn('border-b border-border last:border-0',className)} {...props}/>}
export function TableHead({className,...props}:ComponentProps<'th'>){return <th data-slot="table-head" className={cn('py-3 text-left font-normal text-muted-foreground',className)} {...props}/>}
export function TableCell({className,...props}:ComponentProps<'td'>){return <td data-slot="table-cell" className={cn('py-3 text-right font-medium',className)} {...props}/>}
