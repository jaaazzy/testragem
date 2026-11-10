import type { ComponentProps } from 'react'
import { ChevronDown } from 'lucide-react'
import { cn } from '@/lib/utils'
function NativeSelect({className,...props}:ComponentProps<'select'>){return <div data-slot="native-select-wrapper" className="relative w-full"><select data-slot="native-select" className={cn('h-11 w-full appearance-none rounded-lg border border-input bg-background px-3 pr-10 text-sm outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/25 disabled:cursor-not-allowed disabled:opacity-50',className)} {...props}/><ChevronDown aria-hidden="true" className="pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 text-muted-foreground"/></div>}
function NativeSelectOption(props:ComponentProps<'option'>){return <option data-slot="native-select-option" {...props}/>}
export {NativeSelect,NativeSelectOption}
