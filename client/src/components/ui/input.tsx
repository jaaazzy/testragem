import type { ComponentProps } from 'react'
import { cn } from '@/lib/utils'
export function Input({className,type,...props}:ComponentProps<'input'>){return <input data-slot="input" type={type} className={cn('h-11 w-full min-w-0 rounded-lg border border-input bg-background px-3 text-sm outline-none transition-colors placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/25 disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-destructive',className)} {...props}/>}
