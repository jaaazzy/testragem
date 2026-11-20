package com.testragem.server.strategy.rules;

import com.testragem.server.dataset.model.Candle;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WilderRsiStrategyTest {
    private static Candle candle(String value) {
        var close = new BigDecimal(value);
        return new Candle(Instant.EPOCH, close, close, close, close, BigDecimal.ONE);
    }
    private static List<Signal> feed(WilderRsiStrategy strategy, String... values) {
        return Arrays.stream(values).map(value -> strategy.onCandleClose(candle(value))).toList();
    }
    private static BigDecimal rsi(WilderRsiStrategy strategy) { return strategy.indicatorValues().get("rsi"); }

    @Test void seedsOnlyAfterPeriodChangesAndWaitsForAnActualCrossing() {
        var strategy = new WilderRsiStrategy(3, 30, 70);
        assertThat(strategy.onStart()).isEqualTo(Signal.HOLD);
        assertThat(feed(strategy, "10", "12", "11")).containsOnly(Signal.HOLD);
        assertThat(strategy.indicatorValues()).isEmpty();
        assertThat(feed(strategy, "14")).containsExactly(Signal.HOLD);
        // Changes +2, -1, +3 => mean gain 5/3, mean loss 1/3 => RSI 83 1/3.
        assertThat(rsi(strategy)).isCloseTo(new BigDecimal("83.33333333333333333333333333333333"), within(new BigDecimal("1E-30")));
        // Wilder averages become gain 10/9 and loss 11/9 after the next -3 change.
        feed(strategy, "11");
        assertThat(rsi(strategy)).isCloseTo(new BigDecimal("47.61904761904761904761904761904762"), within(new BigDecimal("1E-30")));
    }

    @Test void buysOnRecoveryAndSellsOnDeclineRatherThanOnEveryOversoldCandle() {
        var strategy = new WilderRsiStrategy(2, 30, 70);
        assertThat(feed(strategy, "100", "99", "98", "99", "100", "99", "98"))
                .containsExactly(Signal.HOLD, Signal.HOLD, Signal.HOLD, Signal.BUY, Signal.HOLD, Signal.SELL, Signal.HOLD);
        assertThat(rsi(strategy)).isEqualByComparingTo("18.75");
    }

    @Test void equalityDoesNotTriggerUntilTheThresholdIsCrossedStrictly() {
        var lower = new WilderRsiStrategy(2, 50, 90);
        assertThat(feed(lower, "100", "99", "98", "99", "100"))
                .containsExactly(Signal.HOLD, Signal.HOLD, Signal.HOLD, Signal.HOLD, Signal.BUY);
        var upper = new WilderRsiStrategy(2, 10, 50);
        assertThat(feed(upper, "100", "101", "102", "101", "100"))
                .containsExactly(Signal.HOLD, Signal.HOLD, Signal.HOLD, Signal.HOLD, Signal.SELL);
    }

    @Test void flatGainOnlyAndLossOnlyHistoriesHaveDefinedValues() {
        var flat = new WilderRsiStrategy(2, 30, 70);
        assertThat(feed(flat, "5", "5", "5", "5")).containsOnly(Signal.HOLD);
        assertThat(rsi(flat)).isEqualByComparingTo("50");
        var rising = new WilderRsiStrategy(2, 30, 70);
        assertThat(feed(rising, "1", "2", "3", "4")).containsOnly(Signal.HOLD);
        assertThat(rsi(rising)).isEqualByComparingTo("100");
        var falling = new WilderRsiStrategy(2, 30, 70);
        assertThat(feed(falling, "4", "3", "2", "1")).containsOnly(Signal.HOLD);
        assertThat(rsi(falling)).isZero();
    }

    @Test void unchangedCloseStillDecaysBothWilderAverages() {
        var strategy = new WilderRsiStrategy(2, 30, 70);
        feed(strategy, "100", "102", "101");
        BigDecimal previous = rsi(strategy);
        feed(strategy, "101");
        assertThat(rsi(strategy)).isEqualByComparingTo(previous);
        feed(strategy, "102");
        assertThat(rsi(strategy)).isCloseTo(new BigDecimal("85.71428571428571428571428571428571"), within(new BigDecimal("1E-30")));
    }

    @Test void observationsAreImmutableSnapshotsAndDifferentRunsHaveIndependentState() {
        var first = new WilderRsiStrategy(2, 30, 70);
        var second = new WilderRsiStrategy(2, 30, 70);
        feed(first, "100", "99", "98");
        var snapshot = first.indicatorValues();
        feed(first, "99");
        assertThat(snapshot.get("rsi")).isZero();
        assertThat(rsi(first)).isEqualByComparingTo("50");
        assertThat(second.indicatorValues()).isEmpty();
        assertThat(feed(second, "100", "99", "98", "99")).containsExactly(Signal.HOLD, Signal.HOLD, Signal.HOLD, Signal.BUY);
    }

    @Test void defaultPeriodNeedsFifteenClosesToSeed() {
        var strategy = new WilderRsiStrategy(14, 30, 70);
        for (int i = 0; i < 14; i++) strategy.onCandleClose(candle("100"));
        assertThat(strategy.indicatorValues()).isEmpty();
        assertThat(strategy.onCandleClose(candle("100"))).isEqualTo(Signal.HOLD);
        assertThat(rsi(strategy)).isEqualByComparingTo("50");
    }

    @Test void rejectsInvalidParameters() {
        assertThatIllegalArgumentException().isThrownBy(() -> new WilderRsiStrategy(1, 30, 70));
        assertThatIllegalArgumentException().isThrownBy(() -> new WilderRsiStrategy(14, 0, 70));
        assertThatIllegalArgumentException().isThrownBy(() -> new WilderRsiStrategy(14, 30, 100));
        assertThatIllegalArgumentException().isThrownBy(() -> new WilderRsiStrategy(14, 70, 30));
        assertThatIllegalArgumentException().isThrownBy(() -> new WilderRsiStrategy(14, 30, 30));
    }
}
