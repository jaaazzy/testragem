package com.testragem.server.backtest.validation;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StrategySelectionValidationTest {
    private final DefaultConfigurationValidator validator = new DefaultConfigurationValidator();

    @Test
    void acceptsAllSupportedStrategyKeysWithoutChangingThem() {
        for (String key : List.of("buy-and-hold", "ma-crossover", "rsi")) {
            assertEquals(key, validator.validateStrategy(key));
        }
    }

    @Test
    void rejectsMissingStrategy() {
        assertRejected(null);
    }

    @Test
    void rejectsEmptyOrBlankStrategy() {
        assertRejected("");
        assertRejected("   ");
    }

    @Test
    void rejectsUnknownStrategy() {
        assertRejected("macd");
    }

    @Test
    void rejectsChangedCaseAndWhitespaceInsteadOfRewritingTheRequestedKey() {
        assertRejected("RSI");
        assertRejected(" rsi ");
    }

    private void assertRejected(String key) {
        var error = assertThrows(ConfigurationValidationException.class,
                () -> validator.validateStrategy(key));
        assertTrue(error.fields().containsKey("strategy"));
    }
}
