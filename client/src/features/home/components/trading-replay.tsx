import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react'
import { Bitcoin, Pause, Play } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { cn } from '@/lib/utils'

const windows = ['1H', '4H', '1D'] as const
type Window = typeof windows[number]
const price = (index: number, scale: number) => 43200 + Math.sin(index * .24 * scale) * 760 + Math.sin(index * .71 * scale) * 210
const formatPrice = (value: number) => value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const y = (value: number) => 230 - (value - 41900) / 2600 * 205
const candleDuration = 2400

export function TradingReplay() {
  const id = useId()
  const panel = useRef<HTMLDivElement>(null)
  const [tick, setTick] = useState(36)
  const clock = useRef(36)
  const [marketTime, setMarketTime] = useState(36)
  const series = useRef<SVGGElement>(null)
  const averageLine = useRef<SVGPathElement>(null)
  const activeBody = useRef<SVGRectElement>(null)
  const activeWick = useRef<SVGLineElement>(null)
  const activeVolume = useRef<SVGRectElement>(null)
  const priceLine = useRef<SVGLineElement>(null)
  const [timeframe, setTimeframe] = useState<Window>('1H')
  const [reduced, setReduced] = useState(() => window.matchMedia('(prefers-reduced-motion: reduce)').matches)
  const [playing, setPlaying] = useState(() => !window.matchMedia('(prefers-reduced-motion: reduce)').matches)
  const [visible, setVisible] = useState(!document.hidden)
  const [inView, setInView] = useState(true)
  const running = playing && visible && inView
  const scale = timeframe === '1H' ? 1 : timeframe === '4H' ? .72 : .5
  const quote = price(marketTime - 1, scale)
  const previous = price(tick - 1, scale)
  const up = quote >= previous
  const candles = Array.from({length:26}, (_, index) => {
    const sample = tick - 25 + index
    const open = price(sample - 1, scale), close = sample === tick ? open : price(sample, scale)
    return { sample, open, close, high: Math.max(open,close) + 55 + Math.abs(Math.sin(sample))*85, low: Math.min(open,close) - 55 - Math.abs(Math.cos(sample))*70 }
  })
  const movingAverage = candles.map((candle,index) => {
    const average = Array.from({length:5},(_,offset) => price(candle.sample-offset,scale)).reduce((a,b)=>a+b,0)/5
    return `${index ? 'L' : 'M'}${index*19-6} ${y(average)}`
  }).join(' ')

  useEffect(() => {
    const media = window.matchMedia('(prefers-reduced-motion: reduce)')
    const changeMotion = () => { setReduced(media.matches); if (media.matches) setPlaying(false) }
    const changeVisibility = () => setVisible(!document.hidden)
    const observer = new IntersectionObserver(([entry]) => setInView(entry.isIntersecting))
    if (panel.current) observer.observe(panel.current)
    media.addEventListener('change', changeMotion)
    document.addEventListener('visibilitychange', changeVisibility)
    return () => { observer.disconnect(); media.removeEventListener('change', changeMotion); document.removeEventListener('visibilitychange', changeVisibility) }
  }, [])

  useLayoutEffect(() => {
    // Candles and average share a single scrolling series. Only the forming
    // candle changes shape; React updates the quote/depth at a calmer 10 Hz.
    const history = Array.from({length:25},(_,index) => {
      const sample = tick - 25 + index
      const value = Array.from({length:5},(_,offset) => price(sample-offset,scale)).reduce((a,b)=>a+b,0)/5
      return `${index ? 'L' : 'M'}${index*19-6} ${y(value)}`
    }).join(' ')
    const priorAverage = Array.from({length:4},(_,offset) => price(tick-1-offset,scale)).reduce((a,b)=>a+b,0)
    function draw() {
      const progress = Math.min(1, Math.max(0, clock.current-tick))
      const open = price(tick-1,scale), close = price(tick-1+progress,scale)
      const high = Math.max(open,close) + progress*(55+Math.abs(Math.sin(tick))*85)
      const low = Math.min(open,close) - progress*(55+Math.abs(Math.cos(tick))*70)
      const color = close>=open ? 'var(--positive)' : 'var(--negative)'
      series.current?.setAttribute('transform', `translate(${-progress*19},0)`)
      activeBody.current?.setAttribute('y', String(y(Math.max(open,close))))
      activeBody.current?.setAttribute('height', String(Math.max(2,Math.abs(y(open)-y(close)))))
      activeBody.current?.setAttribute('fill',color)
      activeWick.current?.setAttribute('y1',String(y(high)))
      activeWick.current?.setAttribute('y2',String(y(low)))
      activeWick.current?.setAttribute('stroke',color)
      const volume = (8+Math.abs(Math.sin(tick*.8))*22)*progress
      activeVolume.current?.setAttribute('y',String(264-volume))
      activeVolume.current?.setAttribute('height',String(volume))
      activeVolume.current?.setAttribute('fill',color)
      averageLine.current?.setAttribute('d',`${history} L469 ${y((priorAverage+close)/5)}`)
      priceLine.current?.setAttribute('y1',String(y(close)))
      priceLine.current?.setAttribute('y2',String(y(close)))
      priceLine.current?.setAttribute('stroke',color)
    }
    draw()
    setMarketTime(clock.current)
    if (!running) return
    if (reduced) {
      const timer = window.setInterval(() => {
        clock.current = Math.floor(clock.current)+1
        setTick(clock.current)
      },candleDuration)
      return () => window.clearInterval(timer)
    }
    let frame = 0
    let previousTime: number | undefined
    let lastQuoteTime = 0
    function animate(now: number) {
      if (previousTime !== undefined) clock.current += Math.min(now-previousTime,64)/candleDuration
      previousTime = now
      draw()
      const nextTick = Math.floor(clock.current)
      if (nextTick !== tick) setTick(nextTick)
      if (now-lastQuoteTime>=100) { setMarketTime(clock.current); lastQuoteTime=now }
      frame = window.requestAnimationFrame(animate)
    }
    frame = window.requestAnimationFrame(animate)
    return () => window.cancelAnimationFrame(frame)
  }, [running,reduced,scale,tick])

  return <Card ref={panel} className="trading-replay min-w-0 gap-0 overflow-hidden py-0" aria-labelledby={`${id}-title`} data-tick={tick}>
    <div className="flex items-center justify-between gap-3 border-b px-5 py-4 sm:px-6">
      <div className="flex min-w-0 items-center gap-3"><span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-[var(--market-icon-background)] text-[var(--market-icon-foreground)]"><Bitcoin className="size-5" aria-hidden="true" /></span><div><h2 id={`${id}-title`} className="text-sm font-semibold">Bitcoin <span className="font-normal text-muted-foreground">/ USDT</span></h2><p className="mt-0.5 text-xs text-muted-foreground">Market replay</p></div></div>
      <span className="inline-flex shrink-0 items-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px] text-muted-foreground"><span className={cn('size-1.5 rounded-full',running ? 'bg-positive' : 'bg-muted-foreground')} aria-hidden="true" />Sample feed</span>
    </div>
    <div className="flex flex-wrap items-end justify-between gap-3 px-5 pb-4 pt-5 sm:px-6">
      <div><p className="text-[clamp(24px,3vw,32px)] font-semibold leading-tight tracking-tight tabular-nums" aria-label={`Illustrative price ${formatPrice(quote)} USDT`}>{formatPrice(quote)} <span className="text-xs font-normal tracking-normal text-muted-foreground">USDT</span></p><p className={cn('mt-1.5 text-xs tabular-nums',up ? 'text-positive' : 'text-negative')}>{up ? '+' : ''}{formatPrice(quote-previous)} <span className="text-muted-foreground">this candle</span></p></div>
      <div role="group" aria-label="Replay timeframe" className="flex gap-1">{windows.map(value => <Button key={value} variant="ghost" size="sm" aria-pressed={timeframe===value} onClick={() => setTimeframe(value)} className={cn('px-2 text-xs text-muted-foreground',timeframe===value&&'bg-secondary text-secondary-foreground')}>{value}</Button>)}</div>
    </div>
    <div className="relative px-3 sm:px-5">
      <svg viewBox="0 0 560 290" className="block w-full" role="img" aria-label="Animated illustrative Bitcoin candlestick replay with a moving average. Synthetic prices, not live market data.">
        <defs><clipPath id={`${id}-clip`}><rect x="0" y="0" width="478" height="268" /></clipPath></defs>
        {[42000,42500,43000,43500,44000].map(level => <g key={level}><line x1="0" x2="478" y1={y(level)} y2={y(level)} stroke="var(--border)" strokeDasharray="3 5" /><text x="490" y={y(level)+4} fill="var(--muted-foreground)" fontSize="11">{(level/1000).toFixed(1)}k</text></g>)}
        <g clipPath={`url(#${id}-clip)`}>
          <g ref={series} data-slot="replay-series">
          {candles.map((candle,index) => {const color=candle.close>=candle.open ? 'var(--positive)' : 'var(--negative)';const active=candle.sample===tick;return <g key={`${timeframe}-${candle.sample}`} transform={`translate(${index*19-6},0)`}><line ref={active ? activeWick : undefined} x1="0" x2="0" y1={y(candle.high)} y2={y(candle.low)} stroke={color} strokeWidth="1.4" /><rect ref={active ? activeBody : undefined} x="-4.5" width="9" y={y(Math.max(candle.open,candle.close))} height={Math.max(2,Math.abs(y(candle.open)-y(candle.close)))} rx="1" fill={color} /><rect ref={active ? activeVolume : undefined} x="-4.5" width="9" y={264-(8+Math.abs(Math.sin(candle.sample*.8))*22)} height={8+Math.abs(Math.sin(candle.sample*.8))*22} rx="1" fill={color} opacity=".2" /></g>})}
          <path ref={averageLine} d={movingAverage} stroke="var(--primary)" strokeWidth="1.8" fill="none" opacity=".85" />
          </g>
          <line ref={priceLine} x1="0" x2="478" y1={y(price(tick-1,scale))} y2={y(price(tick-1,scale))} stroke="var(--positive)" strokeWidth="1" strokeDasharray="4 5" opacity=".65" />
        </g>
        <text x="4" y="284" fill="var(--muted-foreground)" fontSize="10">Earlier</text><text x="442" y="284" fill="var(--muted-foreground)" fontSize="10">Now</text>
      </svg>
    </div>
    <div className="grid grid-cols-2 gap-5 border-t bg-muted/30 px-5 py-4 sm:px-6">
      <div><p className="mb-2 text-[11px] font-medium text-muted-foreground">Bid depth <span className="float-right">BTC</span></p><div className="space-y-1.5">{[1,2,3].map(row => <div key={row} className="relative flex justify-between gap-2 text-[11px] tabular-nums"><span className="replay-depth absolute inset-y-0 right-0 w-full bg-positive/10" style={{transform:`scaleX(${.35+Math.abs(Math.sin(marketTime*.3+row))*.6})`}} /><span className="relative text-positive">{formatPrice(quote-row*12.5)}</span><span className="relative text-muted-foreground">{(Math.abs(Math.sin(marketTime*.2+row))*2+.1).toFixed(3)}</span></div>)}</div></div>
      <div><p className="mb-2 text-[11px] font-medium text-muted-foreground">Ask depth <span className="float-right">BTC</span></p><div className="space-y-1.5">{[1,2,3].map(row => <div key={row} className="relative flex justify-between gap-2 text-[11px] tabular-nums"><span className="replay-depth absolute inset-y-0 right-0 w-full bg-negative/10" style={{transform:`scaleX(${.35+Math.abs(Math.cos(marketTime*.3+row))*.6})`}} /><span className="relative text-negative">{formatPrice(quote+row*12.5)}</span><span className="relative text-muted-foreground">{(Math.abs(Math.cos(marketTime*.2+row))*2+.1).toFixed(3)}</span></div>)}</div></div>
    </div>
    <div className="flex items-center justify-between gap-2 border-t px-5 py-2 sm:px-6"><span className="flex items-center gap-2 text-[11px] text-muted-foreground"><span className="h-px w-4 bg-primary" aria-hidden="true" />Moving average</span><Button variant="ghost" size="sm" className="px-2 text-xs text-muted-foreground" aria-label={playing ? 'Pause market replay' : 'Play market replay'} aria-pressed={playing} onClick={() => setPlaying(current=>!current)}>{playing ? <Pause aria-hidden="true" /> : <Play aria-hidden="true" />}{playing ? 'Pause' : 'Play'}</Button></div>
    <div className="border-t bg-muted/40 px-5 py-3 text-[11px] leading-5 text-muted-foreground sm:px-6"><p>{reduced ? 'Motion reduced. Press Play to explore. ' : ''}Illustrative prices. No live feed or backtest results.</p></div>
  </Card>
}
