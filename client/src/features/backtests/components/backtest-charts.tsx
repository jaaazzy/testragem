import { useLayoutEffect, useMemo, useRef, useState } from 'react'
import type { KeyboardEvent, PointerEvent, ReactNode } from 'react'
import { ChevronLeft, ChevronRight, RotateCcw, ZoomIn, ZoomOut } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { ErrorNotice } from '@/components/error-notice'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { Candle, CandleWindow, SavedRun } from '../api'
import { useResource } from '../use-resource'

const WIDTH = 960, LEFT = 76, RIGHT = 22, TOP = 22, BOTTOM = 36
const EMPTY_CANDLES: Candle[] = []
const money = (value: number) => value.toLocaleString('en-US', { maximumFractionDigits: 2, minimumFractionDigits: 2 })
const compact = (value: number) => value.toLocaleString('en-US', { maximumFractionDigits: 2, notation: Math.abs(value) >= 1e6 ? 'compact' : 'standard' })
const utc = (timestamp: string) => new Date(timestamp).toISOString().slice(0, 16).replace('T', ' ') + ' UTC'
const sameHour = (timestamp: string) => Date.parse(timestamp)

function extent(values: number[], fixed?: [number, number]): [number, number] {
  if (fixed) return fixed
  const low = Math.min(...values), high = Math.max(...values)
  const pad = Math.max((high - low) * .12, Math.abs(high) * .001, .00000001)
  return [low - pad, high + pad]
}

// Break lines at missing values. Warm-up gaps must not appear as calculated observations.
function linePath(values: (number | undefined)[], x: (index: number) => number, y: (value: number) => number) {
  let connected = false
  return values.map((value, index) => {
    if (value === undefined || !Number.isFinite(value)) { connected = false; return '' }
    const command = connected ? 'L' : 'M'
    connected = true
    return `${command}${x(index).toFixed(2)},${y(value).toFixed(2)}`
  }).join(' ')
}

type PlotProps = {
  title: string; label: string; times: string[]; values: number[]; height?: number
  active: number; onInspect: (index: number) => void; domain?: [number, number]
  format?: (value: number) => string
  children: (x: (index: number) => number, y: (value: number) => number, bottom: number, width: number) => ReactNode
}

function Plot({ title, label, times, values, height = 220, active, onInspect, domain, format = compact, children }: PlotProps) {
  const container = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(WIDTH)
  // Match SVG coordinates to the rendered width so mobile labels retain their intended font size.
  useLayoutEffect(() => {
    const node = container.current
    if (!node) return
    const measure = () => setWidth(Math.max(280, Math.floor(node.getBoundingClientRect().width)))
    measure()
    const observer = new ResizeObserver(measure)
    observer.observe(node)
    return () => observer.disconnect()
  }, [])
  const [low, high] = extent(values.length ? values : [0], domain)
  const bottom = height - BOTTOM
  const x = (index: number) => LEFT + (index + .5) / Math.max(1, times.length) * (width - LEFT - RIGHT)
  const y = (value: number) => bottom - (value - low) / (high - low) * (bottom - TOP)
  function inspect(event: PointerEvent<SVGSVGElement>) {
    const box = event.currentTarget.getBoundingClientRect()
    const coordinate = (event.clientX - box.left) / box.width * width
    onInspect(Math.max(0, Math.min(times.length - 1, Math.floor((coordinate - LEFT) / (width - LEFT - RIGHT) * times.length))))
  }
  function keyboard(event: KeyboardEvent<SVGSVGElement>) {
    const direction = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0
    if (direction || event.key === 'Home' || event.key === 'End') {
      event.preventDefault()
      onInspect(event.key === 'Home' ? 0 : event.key === 'End' ? times.length - 1 : Math.max(0, Math.min(times.length - 1, active + direction)))
    }
  }
  return <div ref={container} className="research-plot-scroll">
    <svg viewBox={`0 0 ${width} ${height}`} className="research-plot" role="img" aria-label={label}
      tabIndex={0} onPointerMove={inspect} onPointerDown={inspect} onKeyDown={keyboard}>
      <title>{title}. Use left and right arrow keys to inspect candles.</title>
      {[0, .5, 1].map(ratio => {
        const value = low + (high - low) * ratio
        return <g key={ratio}><line x1={LEFT} x2={width - RIGHT} y1={y(value)} y2={y(value)} className="research-grid" />
          <text x={LEFT - 10} y={y(value) + 4} textAnchor="end" className="research-axis">{format(value)}</text></g>
      })}
      {children(x, y, bottom, width)}
      {active >= 0 && active < times.length && <line x1={x(active)} x2={x(active)} y1={TOP} y2={bottom} className="research-crosshair" />}
      {[0, Math.floor((times.length - 1) / 2), times.length - 1].filter((index, position, array) => array.indexOf(index) === position && index >= 0).map(index =>
        <text key={index} x={x(index)} y={height - 10} textAnchor={index === 0 ? 'start' : index === times.length - 1 ? 'end' : 'middle'} className="research-axis">
          {times[index].slice(5, 16).replace('T', ' ')}
        </text>)}
    </svg>
  </div>
}

function Panel({ title, detail, children }: { title: string; detail: ReactNode; children: ReactNode }) {
  return <section className="research-chart-panel" aria-label={title}>
    <div className="research-chart-heading"><h3 className="text-sm font-semibold">{title}</h3><div className="research-readout">{detail}</div></div>
    {children}
  </section>
}

/** One shared candle window for price, recorded indicators, equity and drawdown. */
export function BacktestCharts({ run }: { run: SavedRun }) {
  const market = useResource<CandleWindow>(`/api/backtests/${run.id}/chart`)
  const [start, setStart] = useState(0)
  const [windowSize, setWindowSize] = useState(80)
  const [inspection, setInspection] = useState<number>()
  const [selectedTrade, setSelectedTrade] = useState<number>()
  const [tradePage, setTradePage] = useState(0)
  const candles = market.data?.candles ?? EMPTY_CANDLES
  // Saved accounting remains visible when the original historical price snapshot is unavailable.
  const timeline = candles.length ? candles : run.result.equityPoints
  // Render only the selected window. All panels share this offset and inspection index.
  const size = Math.min(windowSize, timeline.length)
  const offset = Math.max(0, Math.min(start, timeline.length - size))
  const visible = timeline.slice(offset, offset + size)
  const times = visible.map(point => point.timestamp)
  const active = Math.max(0, Math.min(size - 1, (inspection ?? offset + size - 1) - offset))
  const activeTime = times[active]
  const equityByTime = useMemo(() => new Map(run.result.equityPoints.map(point => [sameHour(point.timestamp), point])), [run])
  // Use persisted observations. Do not recalculate SMA or RSI differently in the browser.
  const indicatorsByTime = useMemo(() => new Map((run.result.indicatorPoints ?? []).map(point => [sameHour(point.timestamp), point.values])), [run])
  const candleByTime = useMemo(() => new Map(candles.map(point => [sameHour(point.timestamp), point])), [candles])
  const visibleCandles = times.map(time => candleByTime.get(sameHour(time)))
  const equity = times.map(time => equityByTime.get(sameHour(time)))
  const indicators = times.map(time => indicatorsByTime.get(sameHour(time)))
  const inspectedCandle = visibleCandles[active]
  const inspectedEquity = equity[active]
  const ma = run.configuration.strategy === 'ma-crossover'
  const rsi = run.configuration.strategy === 'rsi'
  const recordedMa = (run.result.indicatorPoints ?? []).some(point => point.values.fastSma !== undefined || point.values.slowSma !== undefined)
  const recordedRsi = (run.result.indicatorPoints ?? []).some(point => point.values.rsi !== undefined)
  const fillsByTime = useMemo(() => {
    const map = new Map<number, typeof run.result.fills>()
    for (const fill of run.result.fills) { const time = sameHour(fill.executedAt); map.set(time, [...(map.get(time) ?? []), fill]) }
    return map
  }, [run])
  const priceValues = visibleCandles.flatMap(candle => candle ? [candle.low, candle.high] : [])
  if (ma) for (const point of indicators) for (const value of [point?.fastSma, point?.slowSma]) if (value !== undefined) priceValues.push(value)
  const inspect = (index: number) => setInspection(offset + index)
  // Keep the window center while limiting the visible candles to bound rendering work.
  function zoom(direction: number) {
    const next = Math.max(10, Math.min(240, Math.round(windowSize * (direction > 0 ? .5 : 2))))
    const center = offset + size / 2
    setWindowSize(next)
    setStart(Math.max(0, Math.round(center - next / 2)))
    setInspection(undefined)
  }
  // Locate the execution timestamp and move the shared window to that candle.
  function selectTrade(sequence: number, timestamp: string) {
    setSelectedTrade(sequence)
    const index = timeline.findIndex(point => sameHour(point.timestamp) === sameHour(timestamp))
    if (index >= 0) {
      setStart(Math.max(0, Math.min(timeline.length - size, index - Math.floor(size / 2))))
      setInspection(index)
      document.getElementById('historical-price')?.scrollIntoView({ block: 'center', behavior: 'instant' })
    }
  }
  const plotProps = { times, active, onInspect: inspect }
  const visibleFills = run.result.fills.slice(tradePage * 20, tradePage * 20 + 20)

  return <>
    <section className="mt-8 min-w-0" aria-labelledby="analysis-title">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div><h2 id="analysis-title" className="text-lg font-semibold">Historical analysis</h2>
          <p className="mt-1 text-sm text-muted-foreground">Hourly candles · UTC · Entries and exits execute at the candle open.</p></div>
        <div className="flex items-center gap-1" role="group" aria-label="Chart zoom">
          <Button variant="outline" size="icon" aria-label="Zoom in charts" onClick={() => zoom(1)} disabled={size <= 10}><ZoomIn aria-hidden="true" /></Button>
          <Button variant="outline" size="icon" aria-label="Zoom out charts" onClick={() => zoom(-1)} disabled={size >= Math.min(240, timeline.length)}><ZoomOut aria-hidden="true" /></Button>
          <Button variant="ghost" size="icon" aria-label="Reset chart view" onClick={() => { setStart(0); setWindowSize(80); setInspection(undefined); setSelectedTrade(undefined) }}><RotateCcw aria-hidden="true" /></Button>
        </div>
      </div>
      <div className="research-chart-stack mt-5">
        <div id="historical-price">
          <Panel title={`${market.data?.dataset.symbol ?? 'Historical price'}${market.data ? ' / ' + market.data.dataset.timeframe : ''}`} detail={inspectedCandle ? <><span>O {money(inspectedCandle.open)}</span><span>H {money(inspectedCandle.high)}</span><span>L {money(inspectedCandle.low)}</span><span>C {money(inspectedCandle.close)}</span></> : 'Price in USDT'}>
            {market.loading ? <div role="status" aria-label="Loading historical candles" className="p-5"><Skeleton className="h-64 w-full" /></div> : market.error ?
              <div className="p-5"><ErrorNotice title="Price chart unavailable" description={market.error} action={{ label: 'Retry price data', onClick: market.reload }} /></div> : candles.length === 0 ?
                <p className="p-5 text-sm text-muted-foreground">No historical candles were returned for this run.</p> :
                <Plot {...plotProps} title="Historical price" label="Historical candlestick chart with executed buy and sell markers" values={priceValues} height={340}>
                  {(x, y) => <>
                    {visibleCandles.map((candle, index) => candle && <g key={candle.timestamp} data-candle={candle.timestamp}>
                      <line x1={x(index)} x2={x(index)} y1={y(candle.high)} y2={y(candle.low)} className={candle.close >= candle.open ? 'research-up' : 'research-down'} />
                      <rect x={x(index) - Math.min(9, (x(1) - x(0)) * .31)} y={Math.min(y(candle.open), y(candle.close))} width={Math.min(18, (x(1) - x(0)) * .62)} height={Math.max(1.5, Math.abs(y(candle.open) - y(candle.close)))} className={candle.close >= candle.open ? 'research-up research-body-up' : 'research-down research-body-down'} />
                      {(fillsByTime.get(sameHour(candle.timestamp)) ?? []).map(fill => {
                        const buy = fill.side === 'BUY', markerY = buy ? y(candle.low) + 16 : y(candle.high) - 16
                        return <g key={fill.sequenceNumber} className={selectedTrade === fill.sequenceNumber ? 'research-selected-fill' : ''}>
                          <title>{fill.side} #{fill.sequenceNumber} at {money(fill.executionPrice)} USDT (including slippage)</title>
                          <path d={buy ? `M${x(index)},${markerY - 5}l-5,9h10z` : `M${x(index)},${markerY + 5}l-5,-9h10z`} className={buy ? 'research-buy-marker' : 'research-sell-marker'} />
                          <text x={x(index)} y={buy ? markerY + 17 : markerY - 10} textAnchor="middle" className="research-marker-label">{buy ? 'B' : 'S'}</text>
                        </g>
                      })}
                    </g>)}
                    {ma && <><path d={linePath(indicators.map(point => point?.fastSma), x, y)} className="research-fast-line" /><path d={linePath(indicators.map(point => point?.slowSma), x, y)} className="research-slow-line" /></>}
                  </>}
                </Plot>}
            <div className="research-chart-footnote"><span><span className="research-legend-dot research-buy-marker" />B · Buy</span><span><span className="research-legend-dot research-sell-marker" />S · Sell</span>
              {ma && <><span className="research-fast-text">Fast SMA ({run.configuration.parameters.fastPeriod ?? 10}): {indicators[active]?.fastSma === undefined ? 'no value' : money(indicators[active]!.fastSma!)}</span><span className="research-slow-text">Slow SMA ({run.configuration.parameters.slowPeriod ?? 30}): {indicators[active]?.slowSma === undefined ? 'no value' : money(indicators[active]!.slowSma!)}</span></>}
              <span>Markers indicate the execution candle; exact fill prices are in the ledger.</span></div>
            {ma && !recordedMa && <p className="research-empty-note">No SMA values were recorded for this run. Indicator overlays appear when the strategy records its calculations.</p>}
          </Panel>
        </div>
        {rsi && <Panel title={`RSI (${run.configuration.parameters.period ?? 14})`} detail={indicators[active]?.rsi !== undefined ? indicators[active]?.rsi?.toFixed(2) : 'No value at this candle'}>
          {recordedRsi ? <Plot {...plotProps} title="Relative strength index" label="Recorded RSI with entry and exit thresholds" values={[0, 100]} domain={[0, 100]} height={190}>
            {(x, y, _bottom, width) => <>
              {[run.configuration.parameters.lowerThreshold ?? 30, run.configuration.parameters.upperThreshold ?? 70].map(threshold => <g key={threshold}><line x1={LEFT} x2={width - RIGHT} y1={y(threshold)} y2={y(threshold)} className="research-threshold" /><text x={width - RIGHT - 4} y={y(threshold) - 5} textAnchor="end" className="research-axis">{threshold}</text></g>)}
              <path d={linePath(indicators.map(point => point?.rsi), x, y)} className="research-fast-line" />
            </>}
          </Plot> : <p className="research-empty-note">No RSI values were recorded for this run. The panel is ready for the strategy’s calculated values.</p>}
        </Panel>}
        <Panel title="Equity over time" detail={inspectedEquity ? `${money(inspectedEquity.equity)} USDT` : 'Account value in USDT'}>
          {equity.some(Boolean) ? <Plot {...plotProps} title="Equity over time" label="Equity curve: cash plus holdings at candle close" values={[run.configuration.initialCapital, ...equity.flatMap(point => point ? [point.equity] : [])]}>
            {(x, y, _bottom, width) => <><line x1={LEFT} x2={width - RIGHT} y1={y(run.configuration.initialCapital)} y2={y(run.configuration.initialCapital)} className="research-threshold" /><path d={linePath(equity.map(point => point?.equity), x, y)} className="research-fast-line" />{equity[active] && <circle cx={x(active)} cy={y(equity[active]!.equity)} r="3.5" className="research-active-point" />}</>}
          </Plot> : <p className="research-empty-note">No equity points were recorded.</p>}
          <p className="research-chart-footnote">Cash plus holdings at each close. Dashed line: starting capital of {money(run.configuration.initialCapital)} USDT.</p>
        </Panel>
        <Panel title="Drawdown" detail={inspectedEquity ? `−${(inspectedEquity.drawdown * 100).toFixed(2)}% from peak` : 'Decline from account peak'}>
          {equity.some(Boolean) ? <Plot {...plotProps} title="Drawdown" label="Drawdown chart: percentage decline from the running equity peak" values={[0]} domain={[-Math.max(.01, ...equity.map(point => point?.drawdown ?? 0)) * 1.15, 0]} format={value => `${(value * 100).toFixed(1)}%`} height={170}>
            {(x, y) => {
              const values = equity.map(point => point ? -point.drawdown : undefined)
              const path = linePath(values, x, y)
              return <>{values.every(value => value !== undefined) && <path d={`${path} L${x(values.length - 1)},${y(0)} L${x(0)},${y(0)} Z`} className="research-drawdown-area" />}<path d={path} className="research-drawdown-line" />{equity[active] && <circle cx={x(active)} cy={y(-equity[active]!.drawdown)} r="3" className="research-drawdown-point" />}</>
            }}
          </Plot> : <p className="research-empty-note">No drawdown points were recorded.</p>}
        </Panel>
      </div>
      {timeline.length > 0 && <div className="research-navigation">
        <div className="flex items-center gap-1"><Button variant="outline" size="icon" aria-label="Previous chart window" disabled={offset === 0} onClick={() => { setStart(Math.max(0, offset - Math.max(1, Math.floor(size / 2)))); setInspection(undefined) }}><ChevronLeft aria-hidden="true" /></Button>
          <Button variant="outline" size="icon" aria-label="Next chart window" disabled={offset + size >= timeline.length} onClick={() => { setStart(offset + Math.max(1, Math.floor(size / 2))); setInspection(undefined) }}><ChevronRight aria-hidden="true" /></Button></div>
        <label className="research-range-label">Candle window
          <input type="range" min={0} max={Math.max(0, timeline.length - size)} value={offset} disabled={timeline.length <= size} aria-label="Chart start candle" onChange={event => { setStart(Number(event.target.value)); setInspection(undefined) }} />
        </label>
        <span className="text-xs text-muted-foreground tabular-nums">{offset + 1}–{offset + size} of {timeline.length.toLocaleString()} candles</span>
      </div>}
      <div className="research-inspection" role="status" aria-live="off"><span>{activeTime ? utc(activeTime) : 'No candle selected'}</span><span>Hover, tap, or use arrow keys to inspect. All panels share the same window.</span></div>
    </section>

    <section className="mt-9" aria-labelledby="fills-title"><h2 id="fills-title" className="text-lg font-semibold">Trade ledger</h2>
      <p className="mt-1 text-sm text-muted-foreground">Select a trade to locate its execution candle.</p>
      {run.result.fills.length === 0 ? <p className="mt-4 text-sm text-muted-foreground">No trades were executed for this range and strategy.</p> : <>
        <div className="mt-4 overflow-x-auto"><Table aria-label="Executed trades"><TableHeadings />
          <TableBody>{visibleFills.map(fill => <TableRow key={fill.sequenceNumber} data-selected={selectedTrade === fill.sequenceNumber || undefined} className={selectedTrade === fill.sequenceNumber ? 'bg-accent' : undefined}>
            <TableCell><Button variant="link" className="px-0 tabular-nums" aria-label={`Locate ${fill.side.toLowerCase()} trade ${fill.sequenceNumber} on chart`} aria-pressed={selectedTrade === fill.sequenceNumber} onClick={() => selectTrade(fill.sequenceNumber, fill.executedAt)}>{utc(fill.executedAt)}</Button></TableCell>
            <TableCell><span className={fill.side === 'BUY' ? 'text-positive' : 'text-negative'}>{fill.side}</span></TableCell><TableCell className="text-right tabular-nums">{fill.quantity}</TableCell><TableCell className="text-right tabular-nums">{money(fill.executionPrice)}</TableCell><TableCell className="text-right tabular-nums">{money(fill.fee)}</TableCell><TableCell className="text-right tabular-nums">{money(fill.cashAfter)}</TableCell>
          </TableRow>)}</TableBody>
        </Table></div>
        {run.result.fills.length > 20 && <div className="mt-4 flex items-center justify-between gap-3"><Button variant="outline" disabled={tradePage === 0} onClick={() => setTradePage(page => page - 1)}>Previous trades</Button><span className="text-xs text-muted-foreground">Page {tradePage + 1} of {Math.ceil(run.result.fills.length / 20)}</span><Button variant="outline" disabled={(tradePage + 1) * 20 >= run.result.fills.length} onClick={() => setTradePage(page => page + 1)}>Next trades</Button></div>}
      </>}
    </section>
  </>
}

function TableHeadings() {
  return <TableHeader><TableRow>{['Executed (UTC)', 'Side', 'Quantity', 'Price (USDT)', 'Fee', 'Cash after'].map((label, index) => <TableHead key={label} className={index >= 2 ? 'text-right' : undefined}>{label}</TableHead>)}</TableRow></TableHeader>
}
