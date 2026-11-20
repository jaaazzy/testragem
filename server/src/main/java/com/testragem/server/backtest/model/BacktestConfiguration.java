package com.testragem.server.backtest.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record BacktestConfiguration(UUID datasetId, String strategy, String strategyVersion,
        Instant rangeStart, Instant rangeEnd, BigDecimal initialCapital,
        BigDecimal feeBps, BigDecimal slippageBps, BigDecimal allocationPercent,
        Map<String, Integer> parameters) {
    public BacktestConfiguration { parameters = Map.copyOf(parameters); }
}
