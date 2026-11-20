package com.testragem.server.strategy.rules;

import com.testragem.server.dataset.model.Candle;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Closed-candle SMA crossings. Create a new instance for every simulation. */
public final class SmaCrossoverStrategy implements TradingStrategy {
    private final MovingAverage fast;
    private final MovingAverage slow;
    private Integer previousComparison;

    public SmaCrossoverStrategy(int fastPeriod, int slowPeriod) {
        if (fastPeriod < 2 || slowPeriod <= fastPeriod) {
            throw new IllegalArgumentException("SMA periods must satisfy 2 <= fastPeriod < slowPeriod");
        }
        fast = new MovingAverage(fastPeriod);
        slow = new MovingAverage(slowPeriod);
    }

    @Override
    public Signal onCandleClose(Candle candle) {
        fast.add(candle.close());
        slow.add(candle.close());
        if (!slow.ready()) return Signal.HOLD;

        // Compare the averages by cross multiplication so rounding cannot hide a crossing.
        int comparison = fast.sum.multiply(BigDecimal.valueOf(slow.period))
                .compareTo(slow.sum.multiply(BigDecimal.valueOf(fast.period)));
        Signal signal = Signal.HOLD;
        // The first pair of full averages seeds history; it is not a crossing.
        if (previousComparison != null) {
            if (previousComparison <= 0 && comparison > 0) signal = Signal.BUY;
            else if (previousComparison >= 0 && comparison < 0) signal = Signal.SELL;
        }
        previousComparison = comparison;
        // The engine owns positions, costs and next-open execution.
        return signal;
    }

    @Override
    public Map<String, BigDecimal> indicatorValues() {
        Map<String, BigDecimal> values = new HashMap<>();
        if (fast.ready()) values.put("fastSma", fast.value());
        if (slow.ready()) values.put("slowSma", slow.value());
        return Map.copyOf(values);
    }

    /** A rolling sum avoids recalculating a whole window on every candle. */
    private static final class MovingAverage {
        private final int period;
        private final ArrayDeque<BigDecimal> closes = new ArrayDeque<>();
        private BigDecimal sum = BigDecimal.ZERO;

        private MovingAverage(int period) { this.period = period; }

        private void add(BigDecimal close) {
            closes.addLast(close);
            sum = sum.add(close);
            if (closes.size() > period) sum = sum.subtract(closes.removeFirst());
        }

        private boolean ready() { return closes.size() == period; }

        private BigDecimal value() {
            return sum.divide(BigDecimal.valueOf(period), MathContext.DECIMAL128);
        }
    }
}
