import type { ComponentProps } from 'react'
import { cn } from '@/lib/utils'
export function Alert({className,...props}:ComponentProps<'div'>){return <div data-slot="alert" role="note" className={cn('grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 rounded-lg border border-border bg-secondary/50 p-4 text-sm [&>svg]:row-span-2 [&>svg]:mt-0.5 [&>svg]:size-4 [&>svg]:text-primary',className)} {...props}/>}
export function AlertTitle({className,...props}:ComponentProps<'p'>){return <p data-slot="alert-title" className={cn('font-medium',className)} {...props}/>}
export function AlertDescription({className,...props}:ComponentProps<'p'>){return <p data-slot="alert-description" className={cn('col-start-2 text-xs leading-5 text-muted-foreground',className)} {...props}/>}
