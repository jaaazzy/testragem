package com.testragem.server.dataset.controller;

import com.testragem.server.dataset.model.DatasetVersion;
import com.testragem.server.dataset.model.CandleWindow;
import java.time.Instant;
import com.testragem.server.dataset.service.DatasetService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

/** Exposes dataset metadata and bounded candle windows to authenticated clients. */
@RestController
@RequestMapping("/api/datasets")
public class DatasetController {
    private final DatasetService datasets;

    public DatasetController(DatasetService datasets) {
        this.datasets = datasets;
    }

    @GetMapping
    public List<DatasetVersion> list() {
        return datasets.list();
    }

    @GetMapping("/{id}")
    public DatasetVersion get(@PathVariable UUID id) {
        return datasets.get(id);
    }

    @GetMapping("/{id}/candles")
    public CandleWindow candles(@PathVariable UUID id, @RequestParam Instant start, @RequestParam Instant end) {
        return datasets.chart(id, start, end);
    }
}
