package com.testragem.server.backtest.validation;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarmupValidationTest {
    private final DefaultConfigurationValidator validator = new DefaultConfigurationValidator();
    private final Instant start = Instant.parse("2025-01-01T00:00:00Z");

    @Test
    void buyAndHoldNeedsOnlyOneCandle() {
        assertDoesNotThrow(() -> validator.validateWarmup("buy-and-hold", start, start, Map.of()));
    }

    @Test
    void smaAcceptsTheMinimumAndLongerRanges() {
        Map<String, Integer> parameters = Map.of("fastPeriod", 10, "slowPeriod", 30);
        assertDoesNotThrow(() -> validator.validateWarmup("ma-crossover", start, endFor(32), parameters));
        assertDoesNotThrow(() -> validator.validateWarmup("ma-crossover", start, endFor(100), parameters));
    }

    @Test
    void smaRejectsAComparisonWithoutTheNextExecutionCandle() {
        assertTooShort("ma-crossover", 31, Map.of("fastPeriod", 10, "slowPeriod", 30));
    }

    @Test
    void rsiAcceptsTheMinimumAndLongerRanges() {
        Map<String, Integer> parameters = Map.of("period", 14);
        assertDoesNotThrow(() -> validator.validateWarmup("rsi", start, endFor(17), parameters));
        assertDoesNotThrow(() -> validator.validateWarmup("rsi", start, endFor(100), parameters));
    }

    @Test
    void rsiRejectsAComparisonWithoutTheNextExecutionCandle() {
        assertTooShort("rsi", 16, Map.of("period", 14));
    }

    @Test
    void usesCustomPeriodsInsteadOfHardCodedDefaults() {
        assertDoesNotThrow(() -> validator.validateWarmup("ma-crossover", start, endFor(7),
                Map.of("fastPeriod", 2, "slowPeriod", 5)));
        assertTooShort("ma-crossover", 6, Map.of("fastPeriod", 2, "slowPeriod", 5));
        assertDoesNotThrow(() -> validator.validateWarmup("rsi", start, endFor(6), Map.of("period", 3)));
        assertTooShort("rsi", 5, Map.of("period", 3));
    }

    @Test
    void largeIntegerPeriodsCannotOverflowToAnAcceptedMinimum() {
        assertTooShort("ma-crossover", 100, Map.of("slowPeriod", Integer.MAX_VALUE));
        assertTooShort("rsi", 100, Map.of("period", Integer.MAX_VALUE));
    }

    @Test
    void requiresThePeriodFromParameterValidationRatherThanApplyingDefaults() {
        var missingSma = assertThrows(ConfigurationValidationException.class,
                () -> validator.validateWarmup("ma-crossover", start, endFor(100), Map.of()));
        assertTrue(missingSma.fields().containsKey("parameters.slowPeriod"));
        var missingRsi = assertThrows(ConfigurationValidationException.class,
                () -> validator.validateWarmup("rsi", start, endFor(100), null));
        assertTrue(missingRsi.fields().containsKey("parameters.period"));
    }

    @Test
    void rejectsUnknownStrategyBeforeCheckingHistory() {
        var error = assertThrows(ConfigurationValidationException.class,
                () -> validator.validateWarmup("macd", start, endFor(100), Map.of()));
        assertTrue(error.fields().containsKey("strategy"));
    }

    @Test
    void rejectsReversedDates() {
        var error = assertThrows(ConfigurationValidationException.class,
                () -> validator.validateWarmup("buy-and-hold", endFor(3), start, Map.of()));
        assertTrue(error.fields().containsKey("rangeStart"));
    }

    @Test
    void rejectsMissingOrUnalignedDates() {
        var missing = assertThrows(ConfigurationValidationException.class,
                () -> validator.validateWarmup("rsi", null, endFor(17), Map.of("period", 14)));
        assertTrue(missing.fields().containsKey("rangeStart"));
        var unaligned = assertThrows(ConfigurationValidationException.class,
                () -> validator.validateWarmup("rsi", start, endFor(17).plusNanos(1), Map.of("period", 14)));
        assertTrue(unaligned.fields().containsKey("rangeEnd"));
    }

    @Test
    void completedConfigurationModuleReportsReady() {
        assertTrue(validator.ready());
    }

    private Instant endFor(long candleCount) {
        // Both endpoints are included: 32 candles span 31 hours.
        return start.plusSeconds((candleCount - 1) * 3600L);
    }

    private void assertTooShort(String strategy, long candleCount, Map<String, Integer> parameters) {
        var error = assertThrows(ConfigurationValidationException.class,
                () -> validator.validateWarmup(strategy, start, endFor(candleCount), parameters));
        assertTrue(error.fields().containsKey("rangeEnd"));
    }
}
