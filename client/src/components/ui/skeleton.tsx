import type { ComponentProps } from 'react'
import { cn } from '@/lib/utils'

// shadcn/ui Skeleton, using the application's semantic surface tokens.
export function Skeleton({ className, ...props }: ComponentProps<'div'>) {
  return <div data-slot="skeleton" aria-hidden="true" className={cn('animate-pulse rounded-md bg-accent', className)} {...props} />
}
