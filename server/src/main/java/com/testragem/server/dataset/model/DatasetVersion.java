package com.testragem.server.dataset.model;

import java.time.Instant;
import java.util.UUID;

/** Public metadata; the operator's filesystem location is deliberately private. */
public record DatasetVersion(UUID id, String symbol, String timeframe, String source,
        String fingerprint, Instant coverageStart, Instant coverageEnd,
        int candleCount, long missingHours) {}
