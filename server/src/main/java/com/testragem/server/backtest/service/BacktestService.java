package com.testragem.server.backtest.service;

import com.testragem.server.backtest.dto.BacktestHistory;
import com.testragem.server.backtest.dto.BacktestRequest;
import com.testragem.server.backtest.dto.BacktestResponse;
import com.testragem.server.backtest.engine.ExecutionEngine;
import com.testragem.server.backtest.repository.BacktestRepository;
import com.testragem.server.backtest.validation.ConfigurationValidator;
import com.testragem.server.common.error.ApiErrors.Failure;
import com.testragem.server.dataset.service.DatasetService;
import com.testragem.server.dataset.model.CandleWindow;
import com.testragem.server.strategy.service.StrategyCatalogService;
import com.testragem.server.strategy.service.StrategyFactory;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Connects validated settings, historical candles, strategy signals, and saved results. */
@Service
public class BacktestService {
    private final DatasetService datasets;
    private final ConfigurationValidator validator;
    private final StrategyFactory strategies;
    private final StrategyCatalogService catalog;
    private final ExecutionEngine engine;
    private final BacktestRepository repository;
    // One permit allows one calculation at a time in this API instance. No job queue is used.
    private final Semaphore slot = new Semaphore(1);

    public BacktestService(DatasetService datasets, ConfigurationValidator validator,
            StrategyFactory strategies, StrategyCatalogService catalog,
            ExecutionEngine engine, BacktestRepository repository) {
        this.datasets = datasets;
        this.validator = validator;
        this.strategies = strategies;
        this.catalog = catalog;
        this.engine = engine;
        this.repository = repository;
    }

    public BacktestResponse run(UUID owner, BacktestRequest request) {
        if (request == null || request.datasetId() == null || request.strategy() == null
                || !catalog.supports(request.strategy())) {
            throw new Failure(HttpStatus.BAD_REQUEST, "invalid_backtest");
        }
        // Only executable strategies with ready configuration validation may run.
        if (!validator.ready()) throw new Failure(HttpStatus.NOT_IMPLEMENTED, "configuration_module_pending");
        if (!strategies.ready(request.strategy())) throw new Failure(HttpStatus.NOT_IMPLEMENTED, "strategy_module_pending");
        if (!slot.tryAcquire()) throw new Failure(HttpStatus.SERVICE_UNAVAILABLE, "backtest_busy");
        try {
            var dataset = datasets.get(request.datasetId());
            var config = validator.validate(request, datasets.coverage(request.datasetId()));
            // Normalization must preserve the requested resource and strategy.
            if (config == null || !request.datasetId().equals(config.datasetId())
                    || !request.strategy().equals(config.strategy())) {
                throw new Failure(HttpStatus.BAD_REQUEST, "invalid_backtest");
            }
            if (config.rangeStart() != null && config.rangeEnd() != null
                    && Duration.between(config.rangeStart(), config.rangeEnd()).toHours() + 1 > ExecutionEngine.MAX_BARS) {
                throw new Failure(HttpStatus.BAD_REQUEST, "backtest_bar_limit");
            }
            // Each run receives a new strategy instance so indicator state cannot leak between runs.
            var candles = datasets.candles(config.datasetId(), config.rangeStart(), config.rangeEnd());
            var result = engine.run(config, candles, strategies.create(config));
            // Save only after calculation succeeds. A failed calculation creates no result rows.
            return repository.save(owner, config, dataset.fingerprint(), result);
        // Release the calculation slot even when validation, execution, or persistence fails.
        } finally {
            slot.release();
        }
    }

    public BacktestHistory history(UUID owner, int page, int size) {
        return repository.history(owner, page, size);
    }

    public BacktestResponse get(UUID owner, UUID id) {
        return repository.get(owner, id);
    }

    /** Resolve ownership first, and never substitute a different historical snapshot. */
    public CandleWindow chart(UUID owner, UUID id) {
        var run = repository.get(owner, id);
        var config = run.configuration();
        boolean snapshotAvailable = datasets.list().stream().anyMatch(dataset ->
                dataset.id().equals(config.datasetId()) && dataset.fingerprint().equals(run.datasetFingerprint()));
        if (!snapshotAvailable) throw new Failure(HttpStatus.CONFLICT, "dataset_snapshot_unavailable");
        return datasets.chart(config.datasetId(), config.rangeStart(), config.rangeEnd());
    }
}
