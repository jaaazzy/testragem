package com.testragem.server.strategy.service;

import com.testragem.server.strategy.rules.TradingStrategy;

import com.testragem.server.backtest.model.BacktestConfiguration;
public interface StrategyFactory {
    boolean ready(String strategy);
    TradingStrategy create(BacktestConfiguration configuration);
}
