package com.testragem.server.strategy.controller;

import com.testragem.server.strategy.dto.StrategyResponse;
import com.testragem.server.strategy.service.StrategyCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/** Lists strategy names, default parameters, and current execution availability. */
@RestController
@RequestMapping("/api/strategies")
public class StrategyController {
    private final StrategyCatalogService catalog;

    public StrategyController(StrategyCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<StrategyResponse> list() {
        return catalog.list();
    }
}
