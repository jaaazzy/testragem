package com.testragem.server.dataset.model;

import java.util.List;

public record CandleWindow(DatasetVersion dataset, List<Candle> candles) {
    public CandleWindow { candles = List.copyOf(candles); }
}
