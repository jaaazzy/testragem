package com.testragem.server.strategy.service;

import com.testragem.server.backtest.engine.ExecutionEngine;
import com.testragem.server.backtest.model.BacktestConfiguration;
import com.testragem.server.backtest.validation.DefaultConfigurationValidator;
import com.testragem.server.common.error.ApiErrors.Failure;
import com.testragem.server.dataset.model.Candle;
import com.testragem.server.strategy.rules.Signal;
import com.testragem.server.strategy.rules.SmaCrossoverStrategy;
import com.testragem.server.strategy.rules.WilderRsiStrategy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DefaultStrategyFactoryTest {
    private final DefaultStrategyFactory factory = new DefaultStrategyFactory();

    private static BacktestConfiguration settings(String key, int fast, int slow) {
        return new BacktestConfiguration(UUID.randomUUID(), key, "1", Instant.EPOCH,
                Instant.EPOCH.plusSeconds(8 * 3600), new BigDecimal("1000"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100"),
                Map.of("fastPeriod", fast, "slowPeriod", slow));
    }

    private static List<Candle> candles() {
        String[] closes = {"3", "2", "1", "4", "5", "1", "1", "1", "2"};
        var result = new ArrayList<Candle>();
        for (int index = 0; index < closes.length; index++) {
            var close = new BigDecimal(closes[index]);
            var open = index == 4 ? new BigDecimal("10")
                    : index == 6 ? new BigDecimal("12") : close;
            result.add(new Candle(Instant.EPOCH.plusSeconds(index * 3600L),
                    open, open.max(close), open.min(close), close, BigDecimal.ONE));
        }
        return result;
    }

    @Test void exposesAllImplementedStrategiesAsReady() {
        var catalog = new StrategyCatalogService(new DefaultConfigurationValidator(), factory);
        assertThat(catalog.list()).filteredOn(item -> item.available())
                .extracting(item -> item.id()).containsExactly("buy-and-hold", "ma-crossover", "rsi");
        assertThat(factory.ready(null)).isFalse();
        assertThat(factory.ready("unknown")).isFalse();
        for (var key : List.of("unknown")) {
            assertThatThrownBy(() -> factory.create(settings(key, 2, 3)))
                    .isInstanceOf(Failure.class).hasMessage("strategy_module_pending");
        }
    }

    @Test void createsANewBuyAndHoldInstanceForEachRun() {
        var config = settings("buy-and-hold", 2, 3);
        var first = factory.create(config);
        var second = factory.create(config);
        assertThat(first).isInstanceOf(com.testragem.server.strategy.rules.BuyAndHoldStrategy.class)
                .isNotSameAs(second);
        assertThat(first.onStart()).isEqualTo(Signal.BUY);
        assertThat(first.onCandleClose(candles().getFirst())).isEqualTo(Signal.HOLD);
        assertThat(second.onStart()).isEqualTo(Signal.BUY);
    }

    @Test void usesConfiguredPeriodsAndCreatesIndependentState() {
        var config = settings("ma-crossover", 2, 3);
        var first = factory.create(config);
        var second = factory.create(config);
        assertThat(first).isInstanceOf(SmaCrossoverStrategy.class).isNotSameAs(second);
        for (var candle : candles().subList(0, 3)) first.onCandleClose(candle);
        assertThat(first.onCandleClose(candles().get(3))).isEqualTo(Signal.BUY);
        assertThat(second.indicatorValues()).isEmpty();
        assertThat(second.onCandleClose(candles().get(3))).isEqualTo(Signal.HOLD);
        assertThat(second.indicatorValues()).isEmpty();
    }

    @Test void realSmaRunsThroughTheEngineAtNextOpenAndRecordsChartValues() {
        var config = settings("ma-crossover", 2, 3);
        var engine = new ExecutionEngine();
        var result = engine.run(config, candles(), factory.create(config));
        assertThat(result.fills()).extracting(fill -> fill.side()).containsExactly("BUY", "SELL");
        assertThat(result.fills().getFirst().executedAt()).isEqualTo(candles().get(4).timestamp());
        assertThat(result.fills().getFirst().executionPrice()).isEqualByComparingTo("10");
        assertThat(result.fills().getLast().executedAt()).isEqualTo(candles().get(6).timestamp());
        assertThat(result.fills().getLast().executionPrice()).isEqualByComparingTo("12");
        assertThat(result.finalEquity()).isEqualByComparingTo("1200");
        // A BUY at the final close has no following open, so no third fill is created.
        assertThat(result.equityPoints().getLast().positionQuantity()).isZero();
        assertThat(result.indicatorPoints().getFirst().timestamp()).isEqualTo(candles().get(1).timestamp());
        var crossingValues = result.indicatorPoints().stream()
                .filter(point -> point.timestamp().equals(candles().get(3).timestamp()))
                .findFirst().orElseThrow().values();
        assertThat(crossingValues.get("fastSma")).isEqualByComparingTo("2.5");
        assertThat(crossingValues.get("slowSma")).isEqualByComparingTo("2.333333333333333333333333333333333");
        assertThat(engine.run(config, candles(), factory.create(config))).isEqualTo(result);
    }

    @Test void honorsTheDefaultSlowPeriodBeforeProducingSignals() {
        var strategy = factory.create(settings("ma-crossover", 10, 30));
        for (int index = 0; index < 29; index++) {
            assertThat(strategy.onCandleClose(candles().getFirst())).isEqualTo(Signal.HOLD);
        }
        assertThat(strategy.indicatorValues()).containsOnlyKeys("fastSma");
        assertThat(strategy.onCandleClose(candles().getFirst())).isEqualTo(Signal.HOLD);
        assertThat(strategy.indicatorValues()).containsOnlyKeys("fastSma", "slowSma");
    }

    private static BacktestConfiguration rsiSettings() {
        return new BacktestConfiguration(UUID.randomUUID(), "rsi", "1", Instant.EPOCH,
                Instant.EPOCH.plusSeconds(6 * 3600), new BigDecimal("1000"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100"),
                Map.of("period", 2, "lowerThreshold", 30, "upperThreshold", 70));
    }

    private static List<Candle> rsiCandles() {
        String[] closes = {"100", "99", "98", "99", "100", "99", "98"};
        var result = new ArrayList<Candle>();
        for (int index = 0; index < closes.length; index++) {
            var close = new BigDecimal(closes[index]);
            var open = index == 6 ? new BigDecimal("120") : close;
            result.add(new Candle(Instant.EPOCH.plusSeconds(index * 3600L),
                    open, open.max(close), open.min(close), close, BigDecimal.ONE));
        }
        return result;
    }

    @Test void realRsiUsesNextOpenExecutionAndTheSameValuesForCharts() {
        var config = rsiSettings();
        var first = factory.create(config);
        var second = factory.create(config);
        assertThat(first).isInstanceOf(WilderRsiStrategy.class).isNotSameAs(second);
        var engine = new ExecutionEngine();
        var result = engine.run(config, rsiCandles(), first);
        assertThat(result.fills()).extracting(fill -> fill.side()).containsExactly("BUY", "SELL");
        assertThat(result.fills().getFirst().executedAt()).isEqualTo(rsiCandles().get(4).timestamp());
        assertThat(result.fills().getFirst().executionPrice()).isEqualByComparingTo("100");
        assertThat(result.fills().getLast().executedAt()).isEqualTo(rsiCandles().get(6).timestamp());
        assertThat(result.fills().getLast().executionPrice()).isEqualByComparingTo("120");
        assertThat(result.finalEquity()).isEqualByComparingTo("1200");
        assertThat(result.indicatorPoints().getFirst().timestamp()).isEqualTo(rsiCandles().get(2).timestamp());
        assertThat(result.indicatorPoints().getFirst().values().get("rsi")).isZero();
        assertThat(result.indicatorPoints().get(1).values().get("rsi")).isEqualByComparingTo("50");
        assertThat(second.indicatorValues()).isEmpty();
        assertThat(engine.run(config, rsiCandles(), second)).isEqualTo(result);
    }

    @Test void ignoresRsiBuySignalOnTheFinalCandle() {
        var config = rsiSettings();
        var result = new ExecutionEngine().run(config, rsiCandles().subList(0, 4), factory.create(config));
        assertThat(result.fills()).isEmpty();
        assertThat(result.finalEquity()).isEqualByComparingTo("1000");
    }
}
