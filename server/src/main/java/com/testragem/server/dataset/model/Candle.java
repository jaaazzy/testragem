package com.testragem.server.dataset.model;

import java.math.BigDecimal;
import java.time.Instant;

public record Candle(Instant timestamp, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal volume) {}
