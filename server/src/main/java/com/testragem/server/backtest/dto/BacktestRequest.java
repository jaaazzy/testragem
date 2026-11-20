package com.testragem.server.backtest.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record BacktestRequest(UUID datasetId, String strategy, Instant rangeStart, Instant rangeEnd,
        BigDecimal initialCapital, BigDecimal feeBps, BigDecimal slippageBps,
        BigDecimal allocationPercent, Map<String, Integer> parameters) {}
