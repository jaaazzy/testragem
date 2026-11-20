package com.testragem.server.strategy.rules;

import com.testragem.server.dataset.model.Candle;
import java.math.BigDecimal;
import java.util.Map;

/** One fresh instance per run. Only completed candles are exposed. */
public interface TradingStrategy {
    default Signal onStart() { return Signal.HOLD; }
    Signal onCandleClose(Candle candle);

    /**
     * Latest values from the same calculation used by onCandleClose.
     * Supported keys: fastSma, slowSma, rsi. Omit values until warm-up completes.
     * Return empty for strategies with no indicators. Called once after each close.
     */
    default Map<String, BigDecimal> indicatorValues() { return Map.of(); }
}
