package com.testragem.server.dataset.service;

import com.testragem.server.common.error.ApiErrors.Failure;
import com.testragem.server.dataset.model.Candle;
import com.testragem.server.dataset.model.DatasetCoverage;
import com.testragem.server.dataset.model.DatasetVersion;
import com.testragem.server.dataset.model.CandleWindow;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Selects data from the loaded snapshot. CSV parsing and database registration stay in separate classes. */
@Service
public class DatasetService {
    private static final int MAX_CHART_CANDLES = 10_000;
    private final DatasetImporter importer;

    public DatasetService(DatasetImporter importer) {
        this.importer = importer;
    }

    // Offer only the loaded snapshot, not old metadata without available candle data.
    public List<DatasetVersion> list() {
        return importer.loaded().map(loaded -> List.of(loaded.metadata())).orElseGet(List::of);
    }

    public DatasetVersion get(UUID id) {
        return loaded(id).metadata();
    }

    public DatasetCoverage coverage(UUID id) {
        var dataset = get(id);
        return new DatasetCoverage(id, dataset.coverageStart(), dataset.coverageEnd(), dataset.candleCount());
    }

    // Both range endpoints are inclusive. Dates must be within coverage and aligned to UTC hours.
    public List<Candle> candles(UUID id, Instant start, Instant end) {
        var loaded = loaded(id);
        var dataset = loaded.metadata();
        if (start == null || end == null || start.isAfter(end)
                || start.isBefore(dataset.coverageStart()) || end.isAfter(dataset.coverageEnd())
                || start.getNano() != 0 || end.getNano() != 0
                || Math.floorMod(start.getEpochSecond(), 3600) != 0
                || Math.floorMod(end.getEpochSecond(), 3600) != 0) {
            throw new Failure(HttpStatus.BAD_REQUEST, "invalid_date_range");
        }
        long count = Duration.between(start, end).toHours() + 1;
        var selected = loaded.snapshot().candles().stream()
                .filter(c -> !c.timestamp().isBefore(start) && !c.timestamp().isAfter(end)).toList();
        // Every expected hour must exist. Do not interpolate prices across missing candles.
        if (selected.size() != count) throw new Failure(HttpStatus.BAD_REQUEST, "dataset_gap");
        return selected;
    }

    // Bound response size before selecting candles for a chart.
    public CandleWindow chart(UUID id, Instant start, Instant end) {
        var dataset = get(id);
        if (start != null && end != null
                && Duration.between(start, end).toHours() + 1 > MAX_CHART_CANDLES) {
            throw new Failure(HttpStatus.BAD_REQUEST, "chart_bar_limit");
        }
        return new CandleWindow(dataset, candles(id, start, end));
    }

    private DatasetImporter.LoadedDataset loaded(UUID id) {
        return importer.loaded().filter(value -> value.metadata().id().equals(id))
                .orElseThrow(() -> new Failure(HttpStatus.NOT_FOUND, "dataset_not_found"));
    }
}
