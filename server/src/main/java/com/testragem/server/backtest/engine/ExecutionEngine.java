package com.testragem.server.backtest.engine;

import com.testragem.server.dataset.model.Candle;
import com.testragem.server.backtest.model.BacktestConfiguration;
import com.testragem.server.backtest.model.TradeFill;
import com.testragem.server.backtest.model.EquityPoint;
import com.testragem.server.backtest.model.BacktestOutcome;
import com.testragem.server.backtest.model.IndicatorPoint;

import com.testragem.server.strategy.rules.*;
import com.testragem.server.common.error.ApiErrors.Failure;
import java.math.*;
import java.time.Duration;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Long-only, single-position execution. Fractions, costs and accounting belong here, not in strategies. */
@Component
public class ExecutionEngine {
    public static final String VERSION="testragem-java-1";
    public static final int MAX_BARS=10_000;
    private static final BigDecimal ZERO=BigDecimal.ZERO, ONE=BigDecimal.ONE, BPS=new BigDecimal("10000");
    private static final MathContext MC=MathContext.DECIMAL128;
    // Money uses eight decimal places. Ratios use twelve. Quantity rounds down at each purchase.
    private static BigDecimal money(BigDecimal value) { return value.setScale(8,RoundingMode.HALF_UP); }
    private static BigDecimal ratio(BigDecimal value) { return value.setScale(12,RoundingMode.HALF_UP); }

    public BacktestOutcome run(BacktestConfiguration config, List<Candle> candles, TradingStrategy strategy) {
        if (candles.isEmpty() || candles.size()>MAX_BARS || config.initialCapital()==null
                || config.initialCapital().signum()<=0 || config.initialCapital().compareTo(new BigDecimal("1000000000"))>0
                || config.initialCapital().stripTrailingZeros().scale()>8
                || !bounded(config.allocationPercent(),new BigDecimal("100"),false)
                || !bounded(config.feeBps(),BPS,true) || !bounded(config.slippageBps(),BPS,true)) invalid();
        BigDecimal cash=money(config.initialCapital()), quantity=ZERO, peak=cash, maxDrawdown=ZERO, fees=ZERO, slippage=ZERO;
        BigDecimal feeRate=config.feeBps().divide(BPS,MC), slip=config.slippageBps().divide(BPS,MC);
        BigDecimal allocation=config.allocationPercent().divide(new BigDecimal("100"),MC);
        List<TradeFill> fills=new ArrayList<>(); List<EquityPoint> points=new ArrayList<>();
        List<IndicatorPoint> indicators = new ArrayList<>();
        long started=System.nanoTime();
        // onStart can request a first-open entry. Later signals wait for the next candle open.
        Signal pending=Objects.requireNonNull(strategy.onStart());
        for (int index=0; index<candles.size(); index++) {
            Candle candle=candles.get(index);
            if (Thread.currentThread().isInterrupted() || System.nanoTime()-started>Duration.ofSeconds(5).toNanos())
                throw new Failure(HttpStatus.UNPROCESSABLE_ENTITY,"backtest_time_limit");
            if (index>0 && !candle.timestamp().equals(candles.get(index-1).timestamp().plusSeconds(3600))) invalid();
            BigDecimal reference=candle.open();
            if (reference.signum()<=0 || candle.close().signum()<=0) invalid();
            // Enter only while flat. Allocation includes the entry fee inside the available cash budget.
            if (pending==Signal.BUY && quantity.signum()==0) {
                BigDecimal execution=reference.multiply(ONE.add(slip),MC);
                BigDecimal budget=cash.multiply(allocation,MC).setScale(8,RoundingMode.DOWN);
                BigDecimal bought=budget.divide(execution.multiply(ONE.add(feeRate),MC),12,RoundingMode.DOWN);
                if (bought.precision()-bought.scale()>18) invalid();
                if (bought.signum()>0) {
                    BigDecimal notional=execution.multiply(bought,MC), fee=money(notional.multiply(feeRate,MC));
                    BigDecimal debit=money(notional).add(fee);
                    // Separate rounding can exceed budget by the smallest currency unit.
                    if (debit.compareTo(budget)>0) {
                        bought=budget.subtract(fee).max(ZERO).divide(execution,12,RoundingMode.DOWN);
                        notional=execution.multiply(bought,MC); fee=money(notional.multiply(feeRate,MC)); debit=money(notional).add(fee);
                    }
                    if (bought.signum()>0) {
                        cash=money(cash.subtract(debit)); quantity=bought;
                        // Slippage is already in the execution price. Track it without another cash debit.
                        BigDecimal cost=money(execution.subtract(reference).multiply(quantity,MC));
                        fees=fees.add(fee); slippage=slippage.add(cost);
                        fills.add(new TradeFill(fills.size()+1,candle.timestamp(),"BUY",quantity,reference,execution,fee,cost,cash));
                    }
                }
            } else if (pending==Signal.SELL && quantity.signum()>0) {
                BigDecimal execution=reference.multiply(ONE.subtract(slip),MC), notional=execution.multiply(quantity,MC);
                BigDecimal fee=money(notional.multiply(feeRate,MC)), cost=money(reference.subtract(execution).multiply(quantity,MC));
                cash=money(cash.add(money(notional).subtract(fee))); fees=fees.add(fee); slippage=slippage.add(cost);
                fills.add(new TradeFill(fills.size()+1,candle.timestamp(),"SELL",quantity,reference,execution,fee,cost,cash)); quantity=ZERO;
            }
            // Value holdings at the current close. Drawdown measures loss from the running equity peak.
            BigDecimal equity=money(cash.add(quantity.multiply(candle.close(),MC)));
            peak=peak.max(equity); BigDecimal drawdown=ratio(peak.subtract(equity).divide(peak,MC)); maxDrawdown=maxDrawdown.max(drawdown);
            points.add(new EquityPoint(candle.timestamp(),cash,quantity,equity,drawdown));
            // Expose this completed candle after its accounting. A final-close signal has no execution bar.
            pending=Objects.requireNonNull(strategy.onCandleClose(candle));
            // Copy actual observations now, before the next candle changes the strategy's indicator state.
            var values = strategy.indicatorValues();
            if (!values.isEmpty()) indicators.add(new IndicatorPoint(candle.timestamp(), values));
        }
        if (System.nanoTime()-started>Duration.ofSeconds(5).toNanos()) throw new Failure(HttpStatus.UNPROCESSABLE_ENTITY,"backtest_time_limit");
        // Leave final holdings open and value them at the last close. Do not invent an exit trade.
        BigDecimal finalEquity=points.getLast().equity();
        return new BacktestOutcome(finalEquity,ratio(finalEquity.subtract(config.initialCapital()).divide(config.initialCapital(),MC)),ratio(maxDrawdown),money(fees),money(slippage),VERSION,fills,points,indicators);
    }
    private static boolean bounded(BigDecimal value,BigDecimal upper,boolean zero) {
        return value!=null && (zero ? value.signum()>=0 && value.compareTo(upper)<0 : value.signum()>0 && value.compareTo(upper)<=0);
    }
    private static void invalid() { throw new Failure(HttpStatus.BAD_REQUEST,"invalid_backtest"); }
}
