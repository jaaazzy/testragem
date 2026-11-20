package com.testragem.server.backtest.validation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StrategyParameterValidationTest {
    private final DefaultConfigurationValidator validator = new DefaultConfigurationValidator();

    @Test
    void buyAndHoldHasNoIndicatorParameters() {
        assertEquals(Map.of(), validator.validateParameters("buy-and-hold", null));
        assertEquals(Map.of(), validator.validateParameters("buy-and-hold", Map.of()));
        assertInvalid("buy-and-hold", Map.of("period", 14), "parameters");
    }

    @Test
    void suppliesSmaDefaults() {
        assertEquals(Map.of("fastPeriod", 10, "slowPeriod", 30),
                validator.validateParameters("ma-crossover", null));
    }

    @Test
    void suppliesRsiDefaults() {
        assertEquals(Map.of("period", 14, "lowerThreshold", 30, "upperThreshold", 70),
                validator.validateParameters("rsi", Map.of()));
    }

    @Test
    void fillsOnlyMissingParametersWithoutChangingSuppliedValues() {
        assertEquals(Map.of("fastPeriod", 5, "slowPeriod", 30),
                validator.validateParameters("ma-crossover", Map.of("fastPeriod", 5)));
        assertEquals(Map.of("period", 20, "lowerThreshold", 30, "upperThreshold", 70),
                validator.validateParameters("rsi", Map.of("period", 20)));
    }

    @Test
    void treatsNullSupportedValuesAsMissing() {
        Map<String, Integer> sma = new HashMap<>();
        sma.put("fastPeriod", null);
        sma.put("slowPeriod", null);
        assertEquals(Map.of("fastPeriod", 10, "slowPeriod", 30),
                validator.validateParameters("ma-crossover", sma));
        Map<String, Integer> rsi = new HashMap<>();
        rsi.put("period", null);
        rsi.put("lowerThreshold", null);
        rsi.put("upperThreshold", null);
        assertEquals(Map.of("period", 14, "lowerThreshold", 30, "upperThreshold", 70),
                validator.validateParameters("rsi", rsi));
    }

    @Test
    void acceptsCustomSmaPeriodsAtTheirLowerBoundary() {
        var parameters = Map.of("fastPeriod", 2, "slowPeriod", 3);
        assertEquals(parameters, validator.validateParameters("ma-crossover", parameters));
    }

    @Test
    void rejectsSmaPeriodsBelowTwo() {
        for (int period : List.of(-1, 0, 1)) {
            assertInvalid("ma-crossover", Map.of("fastPeriod", period), "parameters.fastPeriod");
            assertInvalid("ma-crossover", Map.of("slowPeriod", period), "parameters.slowPeriod");
        }
    }

    @Test
    void rejectsEqualAndReversedSmaPeriods() {
        assertInvalid("ma-crossover", Map.of("fastPeriod", 30, "slowPeriod", 30), "parameters.fastPeriod");
        assertInvalid("ma-crossover", Map.of("fastPeriod", 31, "slowPeriod", 30), "parameters.fastPeriod");
    }

    @Test
    void acceptsCustomRsiSettingsAtPermittedBoundaries() {
        var parameters = Map.of("period", 2, "lowerThreshold", 1, "upperThreshold", 99);
        assertEquals(parameters, validator.validateParameters("rsi", parameters));
    }

    @Test
    void rejectsRsiPeriodsBelowTwo() {
        for (int period : List.of(-1, 0, 1)) {
            assertInvalid("rsi", Map.of("period", period), "parameters.period");
        }
    }

    @Test
    void rejectsThresholdsOutsideTheOpenZeroToHundredRange() {
        for (int threshold : List.of(-1, 0, 100, 101)) {
            assertInvalid("rsi", Map.of("lowerThreshold", threshold), "parameters.lowerThreshold");
            assertInvalid("rsi", Map.of("upperThreshold", threshold), "parameters.upperThreshold");
        }
    }

    @Test
    void rejectsEqualAndReversedRsiThresholds() {
        assertInvalid("rsi", Map.of("lowerThreshold", 50, "upperThreshold", 50), "parameters.lowerThreshold");
        assertInvalid("rsi", Map.of("lowerThreshold", 70, "upperThreshold", 30), "parameters.lowerThreshold");
    }

    @Test
    void rejectsUnknownAndOtherStrategiesParameterNames() {
        for (String strategy : List.of("buy-and-hold", "ma-crossover", "rsi")) {
            assertInvalid(strategy, Map.of("unknown", 1), "parameters");
        }
        assertInvalid("ma-crossover", Map.of("period", 14), "parameters");
        assertInvalid("rsi", Map.of("fastPeriod", 10), "parameters");
    }

    @Test
    void rejectsNullParameterNamesWithoutNullPointerException() {
        Map<String, Integer> parameters = new HashMap<>();
        parameters.put(null, 10);
        assertInvalid("ma-crossover", parameters, "parameters");
    }

    @Test
    void copiesParametersWithoutModifyingTheInput() {
        Map<String, Integer> input = new HashMap<>(Map.of("fastPeriod", 5));
        Map<String, Integer> result = validator.validateParameters("ma-crossover", input);
        assertEquals(Map.of("fastPeriod", 5), input);
        input.put("fastPeriod", 6);
        assertEquals(5, result.get("fastPeriod"));
        assertThrows(UnsupportedOperationException.class, () -> result.put("fastPeriod", 7));
    }

    @Test
    void rejectsAnUnknownStrategyBeforeApplyingParameterDefaults() {
        assertInvalid("macd", Map.of(), "strategy");
        assertInvalid(null, null, "strategy");
    }

    private void assertInvalid(String strategy, Map<String, Integer> parameters, String field) {
        var error = assertThrows(ConfigurationValidationException.class,
                () -> validator.validateParameters(strategy, parameters));
        assertTrue(error.fields().containsKey(field), error.fields().toString());
    }
}
