package com.testragem.server.backtest.dto;

import java.util.List;

public record BacktestHistory(List<BacktestSummary> items, int page, int size, long total) {}
