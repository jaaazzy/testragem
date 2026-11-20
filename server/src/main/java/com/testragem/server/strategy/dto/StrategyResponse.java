package com.testragem.server.strategy.dto;

import java.util.Map;

public record StrategyResponse(String id, String name, String version,
        boolean available, Map<String, Integer> defaults) {
    public StrategyResponse {
        defaults = Map.copyOf(defaults);
    }
}
