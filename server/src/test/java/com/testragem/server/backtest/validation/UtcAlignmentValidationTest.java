package com.testragem.server.backtest.validation;

import com.testragem.server.dataset.model.DatasetCoverage;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UtcAlignmentValidationTest {
    private final DefaultConfigurationValidator validator = new DefaultConfigurationValidator();
    private final Instant start = Instant.parse("2025-01-01T00:00:00Z");
    private final Instant end = Instant.parse("2025-01-01T02:00:00Z");

    @Test
    void acceptsExactUtcHours() {
        assertDoesNotThrow(() -> validator.validateUtcAlignment(start, end));
    }

    @Test
    void acceptsEqualAlignedDates() {
        assertDoesNotThrow(() -> validator.validateUtcAlignment(start, start));
    }

    @Test
    void acceptsOffsetTimestampThatRepresentsAWholeUtcHour() {
        Instant offsetDate = Instant.parse("2025-01-01T08:00:00+08:00");
        assertEquals(start, offsetDate);
        assertDoesNotThrow(() -> validator.validateUtcAlignment(offsetDate, end));
    }

    @Test
    void rejectsMinutesInStartAndEnd() {
        assertRejected("2025-01-01T00:30:00Z", "rangeStart");
        assertRejected("2025-01-01T01:30:00Z", "rangeEnd");
    }

    @Test
    void rejectsSecondsInStartAndEnd() {
        assertRejected("2025-01-01T00:00:01Z", "rangeStart");
        assertRejected("2025-01-01T01:00:01Z", "rangeEnd");
    }

    @Test
    void rejectsFractionalSecondsInStartAndEnd() {
        assertRejected("2025-01-01T00:00:00.000000001Z", "rangeStart");
        assertRejected("2025-01-01T01:00:00.001Z", "rangeEnd");
    }

    @Test
    void handlesDatesBeforeTheUnixEpoch() {
        Instant aligned = Instant.parse("1969-12-31T23:00:00Z");
        assertDoesNotThrow(() -> validator.validateUtcAlignment(aligned, aligned));
        assertRejected("1969-12-31T23:30:00Z", "rangeStart");
    }

    @Test
    void reportsMissingDateFieldsWithoutNullPointerException() {
        var missingStart = assertThrows(ConfigurationValidationException.class,
                () -> validator.validateUtcAlignment(null, end));
        assertTrue(missingStart.fields().containsKey("rangeStart"));
        var missingEnd = assertThrows(ConfigurationValidationException.class,
                () -> validator.validateUtcAlignment(start, null));
        assertTrue(missingEnd.fields().containsKey("rangeEnd"));
    }

    @Test
    void dateValidationAlsoChecksAlignment() {
        var coverage = new DatasetCoverage(UUID.randomUUID(), start, end, 3);
        var error = assertThrows(ConfigurationValidationException.class,
                () -> validator.validateDates(start.plusSeconds(60), end, coverage));
        assertTrue(error.fields().containsKey("rangeStart"));
    }

    private void assertRejected(String timestamp, String field) {
        Instant value = Instant.parse(timestamp);
        var error = assertThrows(ConfigurationValidationException.class, () ->
                validator.validateUtcAlignment(field.equals("rangeStart") ? value : start,
                        field.equals("rangeEnd") ? value : end));
        assertTrue(error.fields().containsKey(field));
    }
}
