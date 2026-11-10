import { useState } from 'react'
import { Check, RotateCcw, ArrowRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card,CardHeader,CardTitle,CardDescription,CardContent,CardFooter } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { NativeSelect,NativeSelectOption } from '@/components/ui/native-select'
import { Separator } from '@/components/ui/separator'
import { useBacktestDraftStore } from '../stores/use-backtest-draft-store'
import type { StrategyId } from '../types/backtest'
import { strategies } from '../strategies'

export function BacktestDraftPanel(){
  const strategy=useBacktestDraftStore(state=>state.strategy)
  const initialCapital=useBacktestDraftStore(state=>state.initialCapital)
  const setStrategy=useBacktestDraftStore(state=>state.setStrategy)
  const reset=useBacktestDraftStore(state=>state.reset)
  const [notice,setNotice]=useState('')
  return (
    <Card aria-labelledby="draft-title">
      <CardHeader><CardTitle id="draft-title" className="text-lg">Your strategy draft</CardTitle><CardDescription>A few assumptions to start exploring.</CardDescription></CardHeader>
      <Separator/>
      <CardContent className="space-y-6">
        <div className="space-y-3"><Label htmlFor="strategy">Strategy</Label><NativeSelect id="strategy" value={strategy} aria-describedby="strategy-description" onChange={event=>{setStrategy(event.target.value as StrategyId);setNotice('')}}>{strategies.map(option=><NativeSelectOption key={option.id} value={option.id}>{option.name}</NativeSelectOption>)}</NativeSelect><p id="strategy-description" className="min-h-10 text-sm leading-6 text-muted-foreground" aria-live="polite">{strategies.find(option=>option.id===strategy)?.description}</p></div>
        <dl className="flex flex-wrap items-center justify-between gap-3 rounded-lg bg-muted/60 px-4 py-4"><dt className="text-sm font-medium">Starting capital<span className="mt-1 block text-xs font-normal text-muted-foreground">Fixed for this draft</span></dt><dd className="text-lg font-semibold tabular-nums">{initialCapital.toLocaleString('en-US')} <span className="text-xs font-medium text-muted-foreground">USDT</span></dd></dl>
      </CardContent>
      <CardFooter className="flex-col items-stretch gap-4 border-t pt-5">
        <div className="flex flex-wrap items-center justify-between gap-3"><Button variant="outline" onClick={()=>{reset();setNotice('Draft reset to moving-average crossover.')}}><RotateCcw aria-hidden="true"/>Reset draft</Button><Button disabled aria-describedby="execution-note">Run backtest<ArrowRight aria-hidden="true"/></Button></div>
        <p id="execution-note" className="text-xs leading-5 text-muted-foreground">Backtest execution is in development. You can explore strategies and prepare your draft.</p>
        <p role="status" className="flex min-h-5 items-start gap-2 text-xs leading-5 text-positive">{notice&&<><Check className="mt-0.5 size-4 shrink-0" aria-hidden="true"/>{notice}</>}</p>
      </CardFooter>
    </Card>
  )
}
