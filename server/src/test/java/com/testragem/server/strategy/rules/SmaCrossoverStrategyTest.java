package com.testragem.server.strategy.rules;

import com.testragem.server.dataset.model.Candle;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SmaCrossoverStrategyTest {
    static Candle candle(String close) {
        var price = new BigDecimal(close);
        return new Candle(Instant.EPOCH, price, price, price, price, BigDecimal.ONE);
    }

    static List<Signal> feed(SmaCrossoverStrategy strategy, String... closes) {
        return Arrays.stream(closes).map(value -> strategy.onCandleClose(candle(value))).toList();
    }

    @Test void warmsEachAverageAndWaitsForAPreviousFullComparison() {
        var strategy = new SmaCrossoverStrategy(2, 3);
        assertThat(strategy.onStart()).isEqualTo(Signal.HOLD);
        assertThat(strategy.indicatorValues()).isEmpty();
        assertThat(feed(strategy, "1")).containsExactly(Signal.HOLD);
        assertThat(strategy.indicatorValues()).isEmpty();
        assertThat(feed(strategy, "2")).containsExactly(Signal.HOLD);
        assertThat(strategy.indicatorValues()).containsOnlyKeys("fastSma");
        assertThat(strategy.indicatorValues().get("fastSma")).isEqualByComparingTo("1.5");
        // Already above on the first full pair: do not invent an earlier crossing.
        assertThat(feed(strategy, "3", "4")).containsExactly(Signal.HOLD, Signal.HOLD);
        assertThat(strategy.indicatorValues().get("fastSma")).isEqualByComparingTo("3.5");
        assertThat(strategy.indicatorValues().get("slowSma")).isEqualByComparingTo("3");
    }

    @Test void signalsOnlyOnCrossingsAndTreatsEqualityAsTheBoundary() {
        var strategy = new SmaCrossoverStrategy(2, 3);
        assertThat(feed(strategy, "3", "2", "1", "4", "5", "1", "1", "1", "2"))
                .containsExactly(Signal.HOLD, Signal.HOLD, Signal.HOLD,
                        Signal.BUY, Signal.HOLD, Signal.SELL, Signal.HOLD, Signal.HOLD, Signal.BUY);
    }

    @Test void buysAndSellsWhenLeavingEqualityButNeverWhenArrivingAtEquality() {
        var rising = new SmaCrossoverStrategy(2, 3);
        assertThat(feed(rising, "2", "2", "2", "3"))
                .containsExactly(Signal.HOLD, Signal.HOLD, Signal.HOLD, Signal.BUY);
        var falling = new SmaCrossoverStrategy(2, 3);
        assertThat(feed(falling, "2", "2", "2", "1"))
                .containsExactly(Signal.HOLD, Signal.HOLD, Signal.HOLD, Signal.SELL);
        var toEquality = new SmaCrossoverStrategy(2, 3);
        assertThat(feed(toEquality, "3", "2", "1", "3"))
                .containsExactly(Signal.HOLD, Signal.HOLD, Signal.HOLD, Signal.HOLD);
    }

    @Test void flatPricesNeverTradeAndSnapshotsDoNotChange() {
        var strategy = new SmaCrossoverStrategy(2, 3);
        assertThat(feed(strategy, "2", "2", "2", "2", "2"))
                .containsOnly(Signal.HOLD);
        var snapshot = strategy.indicatorValues();
        strategy.onCandleClose(candle("8"));
        assertThat(snapshot.get("fastSma")).isEqualByComparingTo("2");
        assertThat(snapshot.get("slowSma")).isEqualByComparingTo("2");
    }

    @Test void smallCrossingsAreNotLostToDisplayPrecision() {
        var strategy = new SmaCrossoverStrategy(2, 3);
        feed(strategy, "1", "1", "1");
        assertThat(strategy.onCandleClose(candle("1.00000000000000000000000000000000000001")))
                .isEqualTo(Signal.BUY);
    }

    @Test void rejectsInvalidPeriods() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SmaCrossoverStrategy(1, 3));
        assertThatIllegalArgumentException().isThrownBy(() -> new SmaCrossoverStrategy(3, 3));
        assertThatIllegalArgumentException().isThrownBy(() -> new SmaCrossoverStrategy(4, 3));
    }
}
