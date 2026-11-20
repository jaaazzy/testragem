package com.testragem.server.backtest.validation;

import com.testragem.server.backtest.dto.BacktestRequest;
import com.testragem.server.dataset.model.DatasetCoverage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests the public entry point, so passing individual helpers alone cannot hide a missing call. */
class CompleteConfigurationValidationTest {
    private final DefaultConfigurationValidator validator = new DefaultConfigurationValidator();
    private final UUID datasetId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final Instant start = Instant.parse("2025-01-01T00:00:00Z");
    private final Instant end = start.plusSeconds(99 * 3600L);
    private final DatasetCoverage coverage = new DatasetCoverage(datasetId, start, end, 100);

    @Test
    void returnsACompleteBuyAndHoldConfigurationWithFinancialDefaults() {
        var config = validator.validate(request("buy-and-hold", null), coverage);
        assertEquals(datasetId, config.datasetId());
        assertEquals("buy-and-hold", config.strategy());
        assertEquals("1", config.strategyVersion());
        assertEquals(start, config.rangeStart());
        assertEquals(end, config.rangeEnd());
        assertEquals(new BigDecimal("10000"), config.initialCapital());
        assertEquals(BigDecimal.ZERO, config.feeBps());
        assertEquals(BigDecimal.ZERO, config.slippageBps());
        assertEquals(new BigDecimal("100"), config.allocationPercent());
        assertEquals(Map.of(), config.parameters());
        assertTrue(validator.ready());
    }

    @Test
    void appliesSmaDefaultsBeforeCheckingWarmup() {
        var valid = range("ma-crossover", start, start.plusSeconds(31 * 3600L), null);
        var config = validator.validate(valid, coverage);
        assertEquals(Map.of("fastPeriod", 10, "slowPeriod", 30), config.parameters());
        assertInvalid(range("ma-crossover", start, start.plusSeconds(30 * 3600L), null), "rangeEnd");
    }

    @Test
    void appliesRsiDefaultsBeforeCheckingWarmup() {
        var valid = range("rsi", start, start.plusSeconds(16 * 3600L), Map.of());
        assertEquals(Map.of("period", 14, "lowerThreshold", 30, "upperThreshold", 70),
                validator.validate(valid, coverage).parameters());
        assertInvalid(range("rsi", start, start.plusSeconds(15 * 3600L), Map.of()), "rangeEnd");
    }

    @Test
    void usesCustomValidatedPeriodsForWarmup() {
        var sma = range("ma-crossover", start, start.plusSeconds(6 * 3600L),
                Map.of("fastPeriod", 2, "slowPeriod", 5));
        assertEquals(sma.parameters(), validator.validate(sma, coverage).parameters());
        var rsi = range("rsi", start, start.plusSeconds(5 * 3600L),
                Map.of("period", 3, "lowerThreshold", 20, "upperThreshold", 80));
        assertEquals(rsi.parameters(), validator.validate(rsi, coverage).parameters());
    }

    @Test
    void retainsAcceptedFinancialAmountsWithoutRoundingOrAddingAnAllocationPrecisionLimit() {
        var request = financial(new BigDecimal("1234.12345678"), new BigDecimal("12.345678"),
                new BigDecimal("2.5"), new BigDecimal("33.333333333"));
        var config = validator.validate(request, coverage);
        assertEquals(request.initialCapital(), config.initialCapital());
        assertEquals(request.feeBps(), config.feeBps());
        assertEquals(request.slippageBps(), config.slippageBps());
        assertEquals(request.allocationPercent(), config.allocationPercent());
    }

    @Test
    void invokesAllFinancialChecksAndReportsFormFieldNames() {
        assertInvalid(financial(BigDecimal.ZERO, null, null, null), "initialCapital");
        assertInvalid(financial(null, new BigDecimal("-1"), null, null), "feeBps");
        assertInvalid(financial(null, null, new BigDecimal("10000"), null), "slippageBps");
        assertInvalid(financial(null, null, null, BigDecimal.ZERO), "allocationPercent");
        assertInvalid(financial(null, null, null, new BigDecimal("101")), "allocationPercent");
    }

    @Test
    void invokesDateCoverageAndAlignmentChecks() {
        assertInvalid(range("buy-and-hold", null, end, null), "rangeStart");
        assertInvalid(range("buy-and-hold", start, null, null), "rangeEnd");
        assertInvalid(range("buy-and-hold", end, start, null), "rangeStart");
        assertInvalid(range("buy-and-hold", start.minusSeconds(3600), end, null), "rangeStart");
        assertInvalid(range("buy-and-hold", start, end.plusSeconds(3600), null), "rangeEnd");
        assertInvalid(range("buy-and-hold", start.plusSeconds(60), end, null), "rangeStart");
        assertInvalid(range("buy-and-hold", start, end.minusNanos(1), null), "rangeEnd");
    }

    @Test
    void invokesStrategyAndParameterChecks() {
        assertInvalid(request(null, null), "strategy");
        assertInvalid(request("macd", Map.of()), "strategy");
        assertInvalid(request("buy-and-hold", Map.of("period", 14)), "parameters");
        assertInvalid(request("ma-crossover", Map.of("fastPeriod", 30, "slowPeriod", 30)), "parameters.fastPeriod");
        assertInvalid(request("rsi", Map.of("lowerThreshold", 80, "upperThreshold", 20)), "parameters.lowerThreshold");
    }

    @Test
    void missingAndMismatchedDatasetInputsReturnFieldErrors() {
        assertInvalid(null, "datasetId");
        var missingId = new BacktestRequest(null, "buy-and-hold", start, end, null, null, null, null, null);
        assertInvalid(missingId, "datasetId");
        var missing = assertThrows(ConfigurationValidationException.class,
                () -> validator.validate(request("buy-and-hold", null), null));
        assertTrue(missing.fields().containsKey("datasetId"));
        var other = new DatasetCoverage(UUID.fromString("22222222-2222-2222-2222-222222222222"), start, end, 100);
        var mismatch = assertThrows(ConfigurationValidationException.class,
                () -> validator.validate(request("buy-and-hold", null), other));
        assertTrue(mismatch.fields().containsKey("datasetId"));
    }

    @Test
    void invalidCoverageMetadataDoesNotCauseNullPointerException() {
        for (DatasetCoverage invalid : new DatasetCoverage[] {
                new DatasetCoverage(datasetId, null, end, 100),
                new DatasetCoverage(datasetId, start, null, 100),
                new DatasetCoverage(datasetId, end, start, 100),
                new DatasetCoverage(datasetId, start, end, 0)}) {
            var error = assertThrows(ConfigurationValidationException.class,
                    () -> validator.validate(request("buy-and-hold", null), invalid));
            assertTrue(error.fields().containsKey("datasetId"));
        }
    }

    @Test
    void returnsAnImmutableSnapshotAndDoesNotModifyCallerParameters() {
        Map<String, Integer> supplied = new HashMap<>(Map.of("fastPeriod", 5));
        var input = request("ma-crossover", supplied);
        var config = validator.validate(input, coverage);
        assertEquals(Map.of("fastPeriod", 5), supplied);
        supplied.put("fastPeriod", 6);
        assertEquals(5, config.parameters().get("fastPeriod"));
        assertThrows(UnsupportedOperationException.class, () -> config.parameters().put("slowPeriod", 40));
    }

    @Test
    void repeatedValidationOfTheSameInputProducesTheSameConfiguration() {
        var input = request("rsi", Map.of("period", 20));
        assertEquals(validator.validate(input, coverage), validator.validate(input, coverage));
    }

    @Test
    void hugePeriodsFailWarmupInsteadOfOverflowingOrAllocatingHistory() {
        assertInvalid(request("ma-crossover", Map.of("slowPeriod", Integer.MAX_VALUE)), "rangeEnd");
        assertInvalid(request("rsi", Map.of("period", Integer.MAX_VALUE)), "rangeEnd");
    }

    private BacktestRequest request(String strategy, Map<String, Integer> parameters) {
        return range(strategy, start, end, parameters);
    }

    private BacktestRequest range(String strategy, Instant rangeStart, Instant rangeEnd, Map<String, Integer> parameters) {
        return new BacktestRequest(datasetId, strategy, rangeStart, rangeEnd, null, null, null, null, parameters);
    }

    private BacktestRequest financial(BigDecimal capital, BigDecimal fee, BigDecimal slip, BigDecimal allocation) {
        return new BacktestRequest(datasetId, "buy-and-hold", start, end, capital, fee, slip, allocation, null);
    }

    private void assertInvalid(BacktestRequest input, String field) {
        var error = assertThrows(ConfigurationValidationException.class, () -> validator.validate(input, coverage));
        assertTrue(error.fields().containsKey(field), error.fields().toString());
    }
}
