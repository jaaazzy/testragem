package com.testragem.server.backtest.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BacktestSummary(UUID id, String strategy, BigDecimal initialCapital,
        BigDecimal finalEquity, BigDecimal totalReturn, Instant completedAt) {}
