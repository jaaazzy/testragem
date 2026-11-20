package com.testragem.server.strategy.service;

import com.testragem.server.backtest.model.BacktestConfiguration;

import com.testragem.server.strategy.rules.*;
import com.testragem.server.common.error.ApiErrors.Failure;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Only implemented strategies are available. Each run receives its own indicator state. */
@Component
public class DefaultStrategyFactory implements StrategyFactory {
    @Override public boolean ready(String strategy) {
        return "ma-crossover".equals(strategy) || "rsi".equals(strategy) || "buy-and-hold".equals(strategy);
    }
    @Override public TradingStrategy create(BacktestConfiguration configuration) {
        if ("ma-crossover".equals(configuration.strategy())) {
            // ConfigurationValidator supplies and checks both periods before reaching this factory.
            return new SmaCrossoverStrategy(configuration.parameters().get("fastPeriod"),
                    configuration.parameters().get("slowPeriod"));
        }
        if ("rsi".equals(configuration.strategy())) {
            return new WilderRsiStrategy(configuration.parameters().get("period"),
                    configuration.parameters().get("lowerThreshold"),
                    configuration.parameters().get("upperThreshold"));
        }

        if("buy-and-hold".equals(configuration.strategy())){
            return new BuyAndHoldStrategy();
        }
        // Unknown strategies are never substituted with a different implementation.
        throw new Failure(HttpStatus.NOT_IMPLEMENTED, "strategy_module_pending");
    }
}
