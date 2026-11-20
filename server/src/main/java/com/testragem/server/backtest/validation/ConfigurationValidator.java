package com.testragem.server.backtest.validation;

import com.testragem.server.backtest.dto.BacktestRequest;
import com.testragem.server.backtest.model.BacktestConfiguration;
import com.testragem.server.dataset.model.DatasetCoverage;

/** Pure configuration validation without database calls or authenticated-owner input. */
public interface ConfigurationValidator {
    boolean ready();
    BacktestConfiguration validate(BacktestRequest request, DatasetCoverage coverage);
}
