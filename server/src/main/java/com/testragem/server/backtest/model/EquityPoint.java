package com.testragem.server.backtest.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Account state at candle close. Drawdown is a fraction: 0.25 means a 25% decline from peak. */
public record EquityPoint(Instant timestamp, BigDecimal cash, BigDecimal positionQuantity,
        BigDecimal equity, BigDecimal drawdown) {}
