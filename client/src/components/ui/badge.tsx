import type { ComponentProps } from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/lib/utils'
const badgeVariants=cva('inline-flex w-fit shrink-0 items-center gap-1.5 rounded-md border px-2 py-1 text-[11px] font-medium whitespace-nowrap [&>svg]:size-3', {variants:{variant:{default:'border-transparent bg-primary text-primary-foreground',secondary:'border-transparent bg-secondary text-secondary-foreground',outline:'border-border text-muted-foreground'}},defaultVariants:{variant:'default'}})
function Badge({className,variant,...props}:ComponentProps<'span'> & VariantProps<typeof badgeVariants>){return <span data-slot="badge" className={cn(badgeVariants({variant}),className)} {...props}/>}
export {Badge}
