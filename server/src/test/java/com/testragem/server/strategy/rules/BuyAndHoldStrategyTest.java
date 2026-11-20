package com.testragem.server.strategy.rules;

import com.testragem.server.backtest.engine.ExecutionEngine;
import com.testragem.server.backtest.model.BacktestConfiguration;
import com.testragem.server.dataset.model.Candle;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuyAndHoldStrategyTest {

    private static BigDecimal number(String value) {
        return new BigDecimal(value);
    }

    private static List<Candle> candles() {
        return List.of(
                new Candle(
                        Instant.EPOCH,
                        number("100"), // Open
                        number("110"), // High
                        number("100"), // Low
                        number("110"), // Close
                        number("10")   // Volume
                ),
                new Candle(
                        Instant.EPOCH.plusSeconds(3600),
                        number("115"),
                        number("120"),
                        number("115"),
                        number("120"),
                        number("10")
                )
        );
    }

    private static BacktestConfiguration settings(
            String feeBps,
            String allocationPercent) {

        return new BacktestConfiguration(
                UUID.randomUUID(),
                "buy-and-hold",
                "1",
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(3600),
                number("1000"),
                number(feeBps),
                BigDecimal.ZERO,
                number(allocationPercent),
                Map.of()
        );
    }

    @Test
    void requestsBuyAtStart() {
        var strategy = new BuyAndHoldStrategy();

        assertThat(strategy.onStart()).isEqualTo(Signal.BUY);
    }

    @Test
    void holdsOnEveryCompletedCandle() {
        var strategy = new BuyAndHoldStrategy();

        for (Candle candle : candles()) {
            assertThat(strategy.onCandleClose(candle))
                    .isEqualTo(Signal.HOLD);
        }

        assertThat(strategy.indicatorValues()).isEmpty();
    }

    @Test
    void buysOnceAtFirstOpenAndLeavesPositionOpen() {
        var result = new ExecutionEngine().run(
                settings("0", "100"),
                candles(),
                new BuyAndHoldStrategy()
        );

        assertThat(result.fills()).hasSize(1);

        var purchase = result.fills().getFirst();

        assertThat(purchase.side()).isEqualTo("BUY");
        assertThat(purchase.executedAt()).isEqualTo(Instant.EPOCH);
        assertThat(purchase.executionPrice())
                .isEqualByComparingTo("100");
        assertThat(purchase.quantity())
                .isEqualByComparingTo("10");

        // Ten units valued at the final close of 120.
        assertThat(result.finalEquity())
                .isEqualByComparingTo("1200");
        assertThat(result.totalReturn())
                .isEqualByComparingTo("0.20");

        // No closing sale was invented.
        assertThat(result.equityPoints().getLast().positionQuantity())
                .isEqualByComparingTo("10");
    }

    @Test
    void respectsPartialAllocation() {
        var result = new ExecutionEngine().run(
                settings("0", "50"),
                candles(),
                new BuyAndHoldStrategy()
        );

        // Invest 500 and keep 500 in cash.
        assertThat(result.fills().getFirst().quantity())
                .isEqualByComparingTo("5");
        assertThat(result.equityPoints().getLast().cash())
                .isEqualByComparingTo("500");
        assertThat(result.finalEquity())
                .isEqualByComparingTo("1100");
    }

    @Test
    void engineAppliesEntryFees() {
        var result = new ExecutionEngine().run(
                settings("100", "100"), // 100 bps = 1%
                candles(),
                new BuyAndHoldStrategy()
        );

        assertThat(result.fills()).hasSize(1);
        assertThat(result.fills().getFirst().fee()).isPositive();
        assertThat(result.fills().getFirst().quantity())
                .isLessThan(number("10"));
        assertThat(result.totalFees())
                .isEqualByComparingTo(result.fills().getFirst().fee());
        assertThat(result.finalEquity())
                .isLessThan(number("1200"));
    }
}