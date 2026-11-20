package com.testragem.server.dataset.service;

import com.testragem.server.dataset.model.DatasetVersion;
import com.testragem.server.dataset.reader.CsvDatasetReader;
import com.testragem.server.dataset.repository.DatasetRepository;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Captures and registers one immutable operator-provided snapshot at startup. */
@Component
public class DatasetImporter {
    private static final Logger log = LoggerFactory.getLogger(DatasetImporter.class);
    public record LoadedDataset(DatasetVersion metadata, CsvDatasetReader.Snapshot snapshot) {}

    private final CsvDatasetReader reader;
    private final DatasetRepository repository;
    private final String csv;
    private final String source;
    private LoadedDataset loaded;

    public DatasetImporter(CsvDatasetReader reader, DatasetRepository repository,
            @Value("${backtest.dataset-csv:}") String csv,
            @Value("${backtest.dataset-source:Operator-provided historical CSV}") String source) {
        this.reader = reader;
        this.repository = repository;
        this.csv = csv;
        this.source = source;
    }

    // Run once at startup. Parse the file and register metadata before exposing the snapshot.
    @PostConstruct
    void load() throws IOException {
        if (csv.isBlank()) {
            log.warn("No historical dataset configured. Set BACKTEST_DATASET_CSV or local dataset settings and restart.");
            return;
        }
        Path path = Path.of(csv);
        // Capture the candle data now. Later file edits require an application restart.
        var snapshot = reader.read(path);
        loaded = new LoadedDataset(repository.register(snapshot, path, source), snapshot);
        var metadata = loaded.metadata();
        log.info("Historical dataset loaded: {} {}, {} candles, {} to {}, {} missing hours",
                metadata.symbol(), metadata.timeframe(), metadata.candleCount(),
                metadata.coverageStart(), metadata.coverageEnd(), metadata.missingHours());
    }

    public Optional<LoadedDataset> loaded() {
        return Optional.ofNullable(loaded);
    }
}
