package com.testragem.server.backtest.dto;

import java.time.Instant;
import java.util.UUID;
import com.testragem.server.backtest.model.BacktestConfiguration;
import com.testragem.server.backtest.model.BacktestOutcome;

public record BacktestResponse(UUID id, BacktestConfiguration configuration, String datasetFingerprint,
        Instant completedAt, BacktestOutcome result) {}
