package com.testragem.server.strategy.rules;

import com.testragem.server.dataset.model.Candle;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;


public final class WilderRsiStrategy implements TradingStrategy {
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private final int period;
    private final BigDecimal divisor;
    private final BigDecimal weight;
    private final BigDecimal lower;
    private final BigDecimal upper;
    private BigDecimal previousClose;
    private int changes;
    private BigDecimal gainSum = BigDecimal.ZERO;
    private BigDecimal lossSum = BigDecimal.ZERO;
    private BigDecimal averageGain;
    private BigDecimal averageLoss;
    private BigDecimal rsi;

    public WilderRsiStrategy(int period, int lowerThreshold, int upperThreshold) {
        if (period < 2 || lowerThreshold <= 0 || upperThreshold >= 100 || lowerThreshold >= upperThreshold) {
            throw new IllegalArgumentException("RSI requires period >= 2 and 0 < lowerThreshold < upperThreshold < 100");
        }
        this.period = period;
        divisor = BigDecimal.valueOf(period);
        weight = BigDecimal.valueOf(period - 1L);
        lower = BigDecimal.valueOf(lowerThreshold);
        upper = BigDecimal.valueOf(upperThreshold);
    }

    @Override
    public Signal onCandleClose(Candle candle) {
        BigDecimal close = candle.close();
        if (previousClose == null) {
            previousClose = close;
            return Signal.HOLD;
        }
        BigDecimal change = close.subtract(previousClose);
        previousClose = close;
        BigDecimal gain = change.max(BigDecimal.ZERO);
        BigDecimal loss = change.negate().max(BigDecimal.ZERO);
        BigDecimal previousRsi = rsi;

        if (changes < period) {
            gainSum = gainSum.add(gain);
            lossSum = lossSum.add(loss);
            if (++changes < period) return Signal.HOLD;
            // Seed with the arithmetic means of the first period price changes.
            // A period of 14 therefore needs 15 closes before any RSI exists.
            averageGain = gainSum.divide(divisor, MC);
            averageLoss = lossSum.divide(divisor, MC);
        } else {
            // Wilder smoothing retains prior history: (previous average * (period - 1) + change) / period.
            averageGain = averageGain.multiply(weight).add(gain).divide(divisor, MC);
            averageLoss = averageLoss.multiply(weight).add(loss).divide(divisor, MC);
        }

        BigDecimal total = averageGain.add(averageLoss);
        // This equivalent RSI formula handles gain-only (100) and loss-only (0) histories without dividing by zero.
        rsi = total.signum() == 0 ? new BigDecimal("50") : HUNDRED.multiply(averageGain).divide(total, MC);
        // Seeding an oversold or overbought value is not itself a crossing.
        if (previousRsi == null) return Signal.HOLD;
        if (previousRsi.compareTo(lower) <= 0 && rsi.compareTo(lower) > 0) return Signal.BUY;
        if (previousRsi.compareTo(upper) >= 0 && rsi.compareTo(upper) < 0) return Signal.SELL;
        return Signal.HOLD;
    }

    @Override
    public Map<String, BigDecimal> indicatorValues() {
        // Charts receive the exact observation used for the most recent signal.
        return rsi == null ? Map.of() : Map.of("rsi", rsi);
    }
}
