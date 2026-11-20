package com.testragem.server;

import com.testragem.server.dataset.model.Candle;
import com.testragem.server.backtest.model.BacktestConfiguration;

import com.testragem.server.backtest.engine.ExecutionEngine;
import com.testragem.server.strategy.rules.*;
import com.testragem.server.common.error.ApiErrors.Failure;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ExecutionEngineTest {
    static BigDecimal n(String v) { return new BigDecimal(v); }
    static BacktestConfiguration settings(String fee,String slip,String allocation) {
        return new BacktestConfiguration(UUID.randomUUID(),"test-fixture","1",Instant.EPOCH,Instant.EPOCH.plusSeconds(7200),n("1000"),n(fee),n(slip),n(allocation),Map.of());
    }
    static List<Candle> candles() { return List.of(candle(0,"100","110"),candle(1,"120","130"),candle(2,"140","150")); }
    static Candle candle(int index,String open,String close) {return new Candle(Instant.EPOCH.plusSeconds(index*3600L),n(open),n(open).max(n(close)),n(open).min(n(close)),n(close),n("1"));}
    static TradingStrategy enterAtStart() { return new TradingStrategy() {public Signal onStart(){return Signal.BUY;}public Signal onCandleClose(Candle c){return Signal.HOLD;}}; }
    @Test void holdMarksToMarketWithoutInventingAnExit() {
        var result=new ExecutionEngine().run(settings("0","0","100"),candles(),enterAtStart());
        assertThat(result.finalEquity()).isEqualByComparingTo("1500");
        assertThat(result.totalReturn()).isEqualByComparingTo("0.5");
        assertThat(result.fills()).hasSize(1); assertThat(result.fills().getFirst().quantity()).isEqualByComparingTo("10");
        assertThat(result.equityPoints().getLast().positionQuantity()).isEqualByComparingTo("10");
    }
    @Test void signalAtCloseExecutesAtNextOpenAndNeverAtTheSignalPrice() {
        var rule=new TradingStrategy(){int count;public Signal onCandleClose(Candle c){return ++count==1?Signal.BUY:Signal.SELL;}};
        var result=new ExecutionEngine().run(settings("0","0","100"),candles(),rule);
        assertThat(result.fills()).hasSize(2);
        assertThat(result.fills().getFirst().referencePrice()).isEqualByComparingTo("120");
        assertThat(result.fills().getLast().referencePrice()).isEqualByComparingTo("140");
        assertThat(result.finalEquity()).isCloseTo(n("1166.66666667"),within(n("0.00000001")));
    }
    @Test void partialAllocationKeepsUninvestedCash() {
        var result=new ExecutionEngine().run(settings("0","0","25"),candles(),enterAtStart());
        assertThat(result.fills().getFirst().quantity()).isEqualByComparingTo("2.5");
        assertThat(result.fills().getFirst().cashAfter()).isEqualByComparingTo("750");
        assertThat(result.finalEquity()).isEqualByComparingTo("1125");
    }
    @Test void entryFeeIsInsideBudgetAndSlippageIsAdverse() {
        var result=new ExecutionEngine().run(settings("100","100","100"),candles(),enterAtStart());
        var fill=result.fills().getFirst();
        assertThat(fill.executionPrice()).isEqualByComparingTo("101");
        assertThat(fill.quantity()).isCloseTo(n("9.802960494069"),within(n("0.000000000001")));
        assertThat(fill.cashAfter()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(result.totalFees()).isEqualByComparingTo(fill.fee());
        assertThat(result.totalSlippage()).isEqualByComparingTo(fill.slippageCost());
    }
    @Test void noTradeAndUnexecutableLastSignalLeaveCapitalIntact() {
        var rule=new TradingStrategy(){int count;public Signal onCandleClose(Candle c){return ++count==3?Signal.BUY:Signal.HOLD;}};
        var result=new ExecutionEngine().run(settings("0","0","100"),candles(),rule);
        assertThat(result.fills()).isEmpty(); assertThat(result.finalEquity()).isEqualByComparingTo("1000");
    }
    @Test void exitCostsAndSlippageReconcileWithCash() {
        var rule=new TradingStrategy(){public Signal onStart(){return Signal.BUY;}public Signal onCandleClose(Candle c){return Signal.SELL;}};
        var result=new ExecutionEngine().run(settings("100","100","100"),candles(),rule);
        assertThat(result.fills()).hasSize(2);
        assertThat(result.fills().getLast().executionPrice()).isEqualByComparingTo("118.8");
        assertThat(result.finalEquity()).isEqualByComparingTo("1152.94578963");
        assertThat(result.totalFees()).isEqualByComparingTo("21.54690717");
        assertThat(result.equityPoints().getLast().positionQuantity()).isZero();
    }
    @Test void deterministicAccountingAndDrawdown() {
        var engine=new ExecutionEngine();var config=settings("10","5","75");
        assertThat(engine.run(config,candles(),enterAtStart())).isEqualTo(engine.run(config,candles(),enterAtStart()));
        var result=engine.run(settings("0","0","100"),List.of(candle(0,"100","120"),candle(1,"120","90")),enterAtStart());
        assertThat(result.maxDrawdown()).isEqualByComparingTo("0.25");
    }
    @Test void roundingCorrectionIsBoundedEvenWithVerySmallPrices() {
        var result=new ExecutionEngine().run(settings("123","1","33.333333"),List.of(candle(0,"0.00000001","0.00000001")),enterAtStart());
        assertThat(result.fills().getFirst().cashAfter()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }
    @Test void rejectsOversizedOrInvalidRuns() {
        assertThatThrownBy(()->new ExecutionEngine().run(settings("0","0","101"),candles(),enterAtStart())).isInstanceOf(Failure.class);
        assertThatThrownBy(()->new ExecutionEngine().run(settings("0","10000","100"),candles(),enterAtStart())).isInstanceOf(Failure.class);
        assertThatThrownBy(()->new ExecutionEngine().run(settings("0","0","100"),List.of(candle(0,"100","100"),candle(2,"100","100")),enterAtStart())).isInstanceOf(Failure.class);
    }
    @Test void recordsOnlyObservedIndicatorsAfterCloseAndCopiesMutableState() {
        var rule = new TradingStrategy() {
            final Map<String,BigDecimal> current = new HashMap<>();
            int count;
            public Signal onCandleClose(Candle candle) {
                if (++count > 1) current.put("fastSma", candle.close());
                return Signal.HOLD;
            }
            public Map<String,BigDecimal> indicatorValues() { return current; }
        };
        var result = new ExecutionEngine().run(settings("0","0","100"), candles(), rule);
        assertThat(result.indicatorPoints()).hasSize(2);
        assertThat(result.indicatorPoints().getFirst().timestamp()).isEqualTo(candles().get(1).timestamp());
        assertThat(result.indicatorPoints().getFirst().values().get("fastSma")).isEqualByComparingTo("130");
        assertThat(result.indicatorPoints().getLast().values().get("fastSma")).isEqualByComparingTo("150");
        rule.current.put("fastSma", n("999"));
        assertThat(result.indicatorPoints().getLast().values().get("fastSma")).isEqualByComparingTo("150");
        assertThat(new ExecutionEngine().run(settings("0","0","100"), candles(), enterAtStart()).indicatorPoints()).isEmpty();
    }

}
