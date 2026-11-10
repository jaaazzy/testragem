import type { ComponentProps } from 'react'
import * as TabsPrimitive from '@radix-ui/react-tabs'
import { cn } from '@/lib/utils'
function Tabs({className,...props}:ComponentProps<typeof TabsPrimitive.Root>){return <TabsPrimitive.Root data-slot="tabs" className={cn('flex flex-col gap-4',className)} {...props}/>}
function TabsList({className,...props}:ComponentProps<typeof TabsPrimitive.List>){return <TabsPrimitive.List data-slot="tabs-list" className={cn('inline-flex h-11 w-fit items-center justify-center rounded-lg bg-muted p-1 text-muted-foreground',className)} {...props}/>}
function TabsTrigger({className,...props}:ComponentProps<typeof TabsPrimitive.Trigger>){return <TabsPrimitive.Trigger data-slot="tabs-trigger" className={cn('inline-flex h-9 flex-1 items-center justify-center rounded-md px-3 text-xs font-medium whitespace-nowrap outline-none transition-colors focus-visible:ring-[3px] focus-visible:ring-ring/35 disabled:pointer-events-none disabled:opacity-50 data-[state=active]:bg-background data-[state=active]:text-primary data-[state=active]:shadow-sm',className)} {...props}/>}
function TabsContent({className,...props}:ComponentProps<typeof TabsPrimitive.Content>){return <TabsPrimitive.Content data-slot="tabs-content" className={cn('outline-none focus-visible:ring-2 focus-visible:ring-ring',className)} {...props}/>}
export {Tabs,TabsList,TabsTrigger,TabsContent}
