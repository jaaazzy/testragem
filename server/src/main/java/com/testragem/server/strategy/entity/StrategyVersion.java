package com.testragem.server.strategy.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StrategyVersion(
    UUID id,
    String strategy_key,
    String version,
    String parameterSchema,
    String codeFingerprint,
    OffsetDateTime createdAt
){}
