package com.testragem.server.backtest.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Executed trade. Reference price is the candle open; execution price includes adverse slippage. */
public record TradeFill(int sequenceNumber, Instant executedAt, String side, BigDecimal quantity,
        BigDecimal referencePrice, BigDecimal executionPrice, BigDecimal fee,
        BigDecimal slippageCost, BigDecimal cashAfter) {}
