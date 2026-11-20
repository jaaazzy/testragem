package com.testragem.server.strategy.service;

import com.testragem.server.backtest.validation.ConfigurationValidator;
import com.testragem.server.strategy.dto.StrategyResponse;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Describes supported strategies. This catalog does not implement their calculations. */
@Service
public class StrategyCatalogService {
    private final ConfigurationValidator validator;
    private final StrategyFactory strategies;

    public StrategyCatalogService(ConfigurationValidator validator, StrategyFactory strategies) {
        this.validator = validator;
        this.strategies = strategies;
    }

    public List<StrategyResponse> list() {
        return List.of(
                info("buy-and-hold", "Buy and hold", Map.of()),
                info("ma-crossover", "Moving-average crossover", Map.of("fastPeriod", 10, "slowPeriod", 30)),
                info("rsi", "RSI strategy", Map.of("period", 14, "lowerThreshold", 30, "upperThreshold", 70)));
    }

    public boolean supports(String id) {
        return list().stream().anyMatch(strategy -> strategy.id().equals(id));
    }

    // A listed strategy is executable only when its implementation and configuration validation are ready.
    private StrategyResponse info(String id, String name, Map<String, Integer> defaults) {
        return new StrategyResponse(id, name, "1", validator.ready() && strategies.ready(id), defaults);
    }
}
