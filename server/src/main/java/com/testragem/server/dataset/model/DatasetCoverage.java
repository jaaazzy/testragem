package com.testragem.server.dataset.model;

import java.time.Instant;
import java.util.UUID;

public record DatasetCoverage(UUID datasetId, Instant start, Instant end, int candleCount) {}
