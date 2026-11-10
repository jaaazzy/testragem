import { Link } from 'react-router'
import { useEffect } from 'react'
import { ArrowRight, ArrowUpRight, CandlestickChart, SlidersHorizontal, SearchCheck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { SiteHeader } from '@/components/site-header'
import { TradingReplay } from '../components/trading-replay'
import { HeroChessBackdrop } from '../components/hero-chess-backdrop'

const steps=[
  {number:'01',title:'Start with a what-if.',text:'Choose a strategy, starting capital, and the assumptions you want to explore.',icon:SlidersHorizontal},
  {number:'02',title:'Give it some history.',text:'Test on a fixed dataset, using only information available at each point in time.',icon:CandlestickChart},
  {number:'03',title:'Look a little closer.',text:'Compare against buy-and-hold. Explore trades, drawdowns, and the effect of costs.',icon:SearchCheck},
]
export function HomePage({section}:{section?:string}) {
  useEffect(()=>{if(section)document.getElementById(section)?.scrollIntoView({block:'start'});else window.scrollTo(0,0)},[section])
  return (
    <div>
      <a className="skip-link" href="#main-content" onClick={event => { event.preventDefault(); const content = document.getElementById('main-content'); content?.focus(); content?.scrollIntoView({ block: 'start' }) }}>Skip to content</a>
      <SiteHeader/>
      <main id="main-content" tabIndex={-1}>
        <section className="hero-section relative isolate overflow-hidden" aria-labelledby="home-title">
          <HeroChessBackdrop/>
          <div className="relative z-10 mx-auto grid max-w-[1280px] items-center gap-10 px-6 py-12 md:px-10 md:py-16 lg:grid-cols-[1fr_1fr] lg:gap-16">
          <div>
            <h1 id="home-title" className="max-w-[560px] text-[clamp(40px,4.6vw,64px)] leading-[1.13] font-semibold tracking-[-.025em]">A little curiosity.<br/><span className="text-primary">A clearer strategy.</span></h1>
            <p className="mt-6 max-w-[450px] text-base leading-7 text-muted-foreground">A calm place to explore crypto trading ideas. Compare signals, inspect assumptions, and learn from history, at your own pace.</p>
            <div className="mt-8 flex flex-wrap items-center gap-3">
              <Button size="lg" asChild><Link to="/workspace">Start a strategy draft<ArrowRight aria-hidden="true"/></Link></Button>
              <Button variant="link" className="px-1" asChild><Link to="/about">Get to know Testragem<ArrowUpRight aria-hidden="true"/></Link></Button>
            </div>
            <p className="mt-5 max-w-[380px] text-[13px] leading-6 text-muted-foreground">Still growing. Explore the draft workspace while backtesting takes shape.</p>
          </div>
          <TradingReplay/>
          </div>
        </section>

        <div className="border-y bg-muted/60">
          <div className="mx-auto grid max-w-[1280px] grid-cols-2 gap-x-6 gap-y-4 px-6 py-5 text-[13px] md:grid-cols-4 md:px-10">
            {[['Market','BTCUSDT spot'],['Timeframe','1-hour candles'],['Planned coverage','2021 to 2025'],['Current stage','Strategy drafts']].map(([label,value])=><div key={label} className="flex flex-col gap-1.5"><span className="text-muted-foreground">{label}</span><span className="font-medium">{value}</span></div>)}
          </div>
        </div>

        <section id="about" className="mx-auto grid max-w-[1280px] scroll-mt-8 gap-10 px-6 py-16 md:px-10 lg:grid-cols-[1fr_1.05fr] lg:gap-16" aria-labelledby="about-title">
          <div>
            <h2 id="about-title" className="max-w-[480px] text-3xl leading-tight font-semibold tracking-tight md:text-4xl">Make space for<br/>the questions behind a trade.</h2>
            <p className="mt-5 max-w-[460px] text-sm leading-7 text-muted-foreground">Testragem is a crypto backtesting research platform in development. Start with historical Bitcoin data, explore straightforward strategies, and keep the assumptions in view.</p>
            <Button variant="link" className="mt-4 px-0" asChild><Link to="/workspace">Explore your first draft<ArrowRight aria-hidden="true"/></Link></Button>
          </div>
          <Card className="gap-0 bg-muted/30">
            <CardContent>
              <div className="mb-5 flex items-center justify-between gap-3"><h3 className="text-base font-semibold">A clear starting point</h3></div>
              {[['One market to understand','Begin with BTCUSDT spot data and hourly candles.'],['Three strategies to explore','Compare moving averages, RSI, and buy-and-hold.'],['Assumptions you can inspect','Keep capital, trading costs, and benchmarks visible.']].map(([title,text],index)=><div key={title}>{index>0&&<Separator className="my-4"/>}<p className="text-sm font-medium">{title}</p><p className="mt-1.5 text-sm leading-6 text-muted-foreground">{text}</p></div>)}
            </CardContent>
          </Card>
        </section>

        <section id="protocol" className="scroll-mt-8 border-t bg-muted/50" aria-labelledby="protocol-title">
          <div className="mx-auto max-w-[1280px] px-6 py-16 md:px-10">
            <div className="mb-9 flex flex-wrap items-center justify-between gap-4"><h2 id="protocol-title" className="text-3xl font-semibold tracking-tight">One idea. A few small steps.</h2><Badge variant="outline">The planned research workflow</Badge></div>
            <ol className="grid gap-8 md:grid-cols-3 md:gap-10">
              {steps.map(({number,title,text,icon:Icon})=><li key={number} className="border-t pt-5"><div className="mb-5 flex items-center gap-3 text-primary"><Icon className="size-5" aria-hidden="true"/><span className="text-xs font-medium">{number}</span></div><h3 className="text-base font-semibold">{title}</h3><p className="mt-3 max-w-[330px] text-sm leading-6 text-muted-foreground">{text}</p></li>)}
            </ol>
          </div>
        </section>
      </main>
      <footer className="border-t"><div className="mx-auto flex max-w-[1280px] flex-wrap items-center justify-between gap-3 px-6 py-6 text-xs leading-5 text-muted-foreground md:px-10"><span>testragem / Built for the curious.</span><span>Historical performance is a starting point, not a promise.</span></div></footer>
    </div>
  )
}
