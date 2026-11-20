import { AuthError } from '@/features/auth/api'

export type Dataset = { id:string; symbol:string; timeframe:string; source:string; fingerprint:string; coverageStart:string; coverageEnd:string; candleCount:number; missingHours:number }
export type StrategyInfo = { id:string; name:string; version:string; available:boolean; defaults:Record<string,number> }
export type Configuration = { datasetId:string; strategy:string; strategyVersion?:string; rangeStart:string; rangeEnd:string; initialCapital:number; feeBps:number; slippageBps:number; allocationPercent:number; parameters:Record<string,number> }
export type Fill = { sequenceNumber:number; executedAt:string; side:string; quantity:number; referencePrice:number; executionPrice:number; fee:number; slippageCost:number; cashAfter:number }
export type EquityPoint = { timestamp:string; cash:number; positionQuantity:number; equity:number; drawdown:number }
export type Candle = { timestamp:string; open:number; high:number; low:number; close:number; volume:number }
export type CandleWindow = { dataset:Dataset; candles:Candle[] }
export type IndicatorPoint = { timestamp:string; values:Partial<Record<'fastSma'|'slowSma'|'rsi',number>> }
export type Outcome = { finalEquity:number; totalReturn:number; maxDrawdown:number; totalFees:number; totalSlippage:number; engineVersion:string; fills:Fill[]; equityPoints:EquityPoint[]; indicatorPoints?:IndicatorPoint[] }
export type SavedRun = { id:string; configuration:Configuration; datasetFingerprint:string; completedAt:string; result:Outcome }
export type Summary = { id:string; strategy:string; initialCapital:number; finalEquity:number; totalReturn:number; completedAt:string }
export type History = { items:Summary[]; page:number; size:number; total:number }
export class BacktestRequestError extends AuthError {
  constructor(status:number,code:string,public fields:Record<string,string>={}){super(status,code)}
}

// Use the same-origin session cookie. Fetch a fresh CSRF token before each modifying request.
async function request<T>(path:string,signal?:AbortSignal,body?:object,method=body?'POST':'GET'):Promise<T> {
  const headers:Record<string,string>={Accept:'application/json'}
  if(body){const csrf=await request<{token:string;headerName:string}>('/api/auth/csrf',signal);headers[csrf.headerName]=csrf.token;headers['Content-Type']='application/json'}
  // Combine route cancellation with a timeout so abandoned or stalled requests do not keep running.
  const response=await fetch(path,{method,headers,body:body?JSON.stringify(body):undefined,credentials:'same-origin',cache:'no-store',signal:signal?AbortSignal.any([signal,AbortSignal.timeout(15000)]):AbortSignal.timeout(15000)})
  if(!response.ok){const failure=await response.json().catch(()=>({}));if(response.status===401)window.dispatchEvent(new Event('focus'));throw new BacktestRequestError(response.status,failure.error??'request_failed',failure.fields??{})}
  return response.json()
}
export const backtestApi={get:<T,>(path:string,signal:AbortSignal)=>request<T>(path,signal),post:<T,>(path:string,body:object,signal:AbortSignal)=>request<T>(path,signal,body),put:<T,>(path:string,body:object,signal:AbortSignal)=>request<T>(path,signal,body,'PUT'),run:(configuration:Configuration,signal:AbortSignal)=>request<SavedRun>('/api/backtests',signal,configuration)}
// Translate server error codes into recovery instructions. Never display raw server diagnostics.
export function backtestError(error:unknown):string {
  if(error instanceof DOMException && error.name==='TimeoutError') return 'The server took too long to respond. Try again shortly.'
  if(error instanceof AuthError){
    const known=({dataset_snapshot_unavailable:'The original dataset is not loaded. Reconnect that snapshot to see its price chart. Saved results remain available.',chart_bar_limit:'Choose a range of at most 10,000 hourly candles.',dataset_not_found:'This dataset is not currently loaded on the server. Refresh the dataset list or select another dataset.',configuration_module_pending:'Backtest configuration is not available yet.',strategy_module_pending:'This strategy is not available to run yet. Choose an available strategy.',dataset_gap:'This date range contains missing candles. Choose a continuous range.',invalid_date_range:'Choose UTC hours within dataset coverage.',backtest_bar_limit:'Choose a smaller range: runs support up to 10,000 hourly candles.',backtest_time_limit:'This run exceeded its time limit. Try a shorter range.',backtest_busy:'Another simulation is running. Please try again shortly.',backtest_not_found:'This backtest is unavailable. Return to your history and choose another run.',preset_not_found:'This preset is unavailable. Refresh the list and choose another preset.',invalid_configuration:'Review the highlighted settings, then try again.',invalid_pagination:'This page is unavailable. Return to the first page of your history.',invalid_backtest:'Check the settings and try again.'} as Record<string,string>)[error.code]
    if(known)return known
    if(error.status===401)return 'Your session has expired. Sign in again to continue.'
    if(error.status===403)return 'The request could not be authorized. Refresh the page and try again.'
    if(error.status===429)return 'Too many requests. Wait a few minutes before trying again.'
    if(error.status===404)return 'This item is unavailable. It may no longer be accessible to your account.'
    if(error.status>=500)return 'The service is temporarily unavailable. Try again shortly.'
    return 'The request could not be completed. Check your settings and try again.'
  }
  return 'Could not reach the server. Check your connection and try again.'
}
