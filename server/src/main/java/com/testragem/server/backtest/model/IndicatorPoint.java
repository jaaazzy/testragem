package com.testragem.server.backtest.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** Indicator values observed after a completed candle, never chart-side recalculations. */
public record IndicatorPoint(Instant timestamp, Map<String, BigDecimal> values) {
    public IndicatorPoint {
        values = Map.copyOf(values);
        if (!java.util.Set.of("fastSma", "slowSma", "rsi").containsAll(values.keySet())) {
            throw new IllegalArgumentException("Unsupported indicator series");
        }
    }
}
