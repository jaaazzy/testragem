package com.testragem.server.backtest.validation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.testragem.server.backtest.dto.BacktestRequest;
import com.testragem.server.dataset.model.DatasetCoverage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultConfigurationValidatorTest {

    private final DefaultConfigurationValidator validator =
            new DefaultConfigurationValidator();

    private BacktestRequest requestWithDataset(UUID datasetId){
        return new BacktestRequest(
                datasetId,
                "buy-and-hold",
                null, null,
                null, null, null, null,
                Map.of()
        );
    }

    private DatasetCoverage coverageWithDataset(UUID datasetId){
        return new DatasetCoverage(
                datasetId,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T02:00:00Z"),
                3
        );
    }

    private final DatasetCoverage dateCoverage =
            coverageWithDataset(UUID.randomUUID());


    @Test
    void returnsDefaultWhenCapitalIsNull(){
        BigDecimal result = validator.validateCapital(null);
        assertEquals(new BigDecimal("10000"), result);
    }

    @Test
    void rejectsZeroCapital(){
        ConfigurationValidationException error = assertThrows(
                ConfigurationValidationException.class,
                () -> validator.validateCapital(BigDecimal.ZERO)
        );
    }

    @Test
    void rejectsNegativeCapital(){
        assertThrows(
                ConfigurationValidationException.class,
                () -> validator.validateCapital(new BigDecimal("-1"))
        );
    }

    @Test
    void acceptsMaximumCapital(){
        BigDecimal input = new BigDecimal("1000000000");

        assertEquals(input, validator.validateCapital(input));
    }

    @Test
    void rejectsAboveMaximumCapital(){
        assertThrows(
                ConfigurationValidationException.class,
                () -> validator.validateCapital(new BigDecimal("1000000000.01"))
        );
    }

    @Test
    void acceptsEightDecimalPlaces(){
        BigDecimal input = new BigDecimal("1.12345678");

        assertEquals(input, validator.validateCapital(input));
    }

    @Test
    void rejectsNineDecimalPlaces(){
        assertThrows(
          ConfigurationValidationException.class,
                () -> validator.validateCapital(new BigDecimal("1.123456789"))
        );
    }

    @Test
    void acceptTrailingZeroes(){
        BigDecimal input = new BigDecimal("1.00000000");

        assertEquals(input, validator.validateCapital(input));
    }

    @Test
    void returnsDefaultWhenCostIsNull(){
        BigDecimal result = validator.validateCost("Cost", null);

        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    void rejectNegativeNumber(){
        assertThrows(
                ConfigurationValidationException.class,
                () -> validator.validateCost("Cost", new BigDecimal("-1"))
        );
    }

    @Test
    void rejectsCostAtUpperLimit(){
        assertThrows(
                ConfigurationValidationException.class,
                () -> validator.validateCost("Cost", new BigDecimal("10000"))
        );
    }

    @Test
    void acceptsCostBelowUpperLimit(){
        BigDecimal input = new BigDecimal("5000");
        assertEquals(input, validator.validateCost("Cost", input));
    }

    @Test
    void acceptsSixDecimalPlaces(){
        BigDecimal input = new BigDecimal("1.123456");

        assertEquals(input, validator.validateCost("Cost", input));
    }

    @Test
    void rejectsMoreThanSixDecimalPlaces(){
        BigDecimal input = new BigDecimal("1.12345678");
        assertThrows(
          ConfigurationValidationException.class,
                () -> validator.validateCost("Cost", input)
        );
    }

    @Test
    void acceptsTrailingZerosBeyondSixDecimalPlaces() {
        BigDecimal input = new BigDecimal("1.000000000");

        assertEquals(input, validator.validateCost("feeBps", input));
    }

    @Test
    void acceptsZeroCost(){
        assertEquals(
               BigDecimal.ZERO,
               validator.validateCost("feeBps", BigDecimal.ZERO)
        );
    }


    @Test
    void acceptsTrailingZeroes(){
        BigDecimal input = new BigDecimal("1.000000");
        assertEquals(input, validator.validateCost("Cost", input));
    }


    @Test
    void reportsTheSlippageFieldWhenInvalid() {
        ConfigurationValidationException error = assertThrows(
                ConfigurationValidationException.class,
                () -> validator.validateCost("slippageBps", new BigDecimal("-1"))
        );

        assertTrue(error.fields().containsKey("slippageBps"));
    }
    @Test
    void returnsDefaultNull(){
        BigDecimal result = validator.validateAllocation(null);

        assertEquals(new BigDecimal("100"), result);
    }

    @Test
    void rejectsZeroAllocation(){
        assertThrows(
        ConfigurationValidationException.class,
        () -> validator.validateAllocation(BigDecimal.ZERO)
        );
    }

    @Test
    void acceptsNotMoreThanAllocationDefault(){
        BigDecimal input = new BigDecimal("100");
        assertEquals(input, validator.validateAllocation(input));
    }

    @Test
    void rejectsMoreThanAllocationDefault(){
        assertThrows(
                ConfigurationValidationException.class,
                () -> validator.validateAllocation(new BigDecimal("100.01"))
        );
    }

    @Test
    void acceptsMatchingDatasetIds() {
        UUID id = UUID.randomUUID();

        BacktestRequest request = requestWithDataset(id);
        DatasetCoverage coverage = coverageWithDataset(id);

        assertDoesNotThrow(
                () -> validator.validateDataset(request, coverage)
        );
    }

    @Test
    void rejectsMissingDatasetId() {
        BacktestRequest request = requestWithDataset(null);
        DatasetCoverage coverage = coverageWithDataset(UUID.randomUUID());

        ConfigurationValidationException error = assertThrows(
                ConfigurationValidationException.class,
                () -> validator.validateDataset(request, coverage)
        );

        assertTrue(error.fields().containsKey("datasetId"));
    }

    @Test
    void rejectsMissingCoverageObject() {
        BacktestRequest request = requestWithDataset(UUID.randomUUID());

        assertThrows(
                ConfigurationValidationException.class,
                () -> validator.validateDataset(request, null)
        );
    }

    @Test
    void rejectsDifferentDatasetIds() {
        UUID requestId = UUID.fromString(
                "11111111-1111-1111-1111-111111111111"
        );
        UUID coverageId = UUID.fromString(
                "22222222-2222-2222-2222-222222222222"
        );

        assertThrows(
                ConfigurationValidationException.class,
                () -> validator.validateDataset(
                        requestWithDataset(requestId),
                        coverageWithDataset(coverageId)
                )
        );
    }

    @Test
    void rejectsMissingRequestObject() {
        DatasetCoverage coverage = coverageWithDataset(UUID.randomUUID());

        assertThrows(
                ConfigurationValidationException.class,
                () -> validator.validateDataset(null, coverage)
        );
    }

    @Test
    void rejectMissingCoverageDatasetId(){
        BacktestRequest request = requestWithDataset(UUID.randomUUID());

        assertThrows(
          ConfigurationValidationException.class,
                ()-> validator.validateDataset(request, coverageWithDataset(null))
        );
    }

       @Test

       void acceptBothDatesWithinCoverage(){
            Instant start = Instant.parse("2025-01-01T00:00:00Z");
            Instant end = Instant.parse("2025-01-01T01:00:00Z");

            assertDoesNotThrow(
                    () -> validator.validateDates(start, end, dateCoverage)
            );
       }

    @Test
    void acceptsExactCoverageBoundaries() {
        assertDoesNotThrow(
                () -> validator.validateDates(
                        dateCoverage.start(),
                        dateCoverage.end(),
                        dateCoverage
                )
        );
    }

    @Test
    void acceptsEqualStartAndEnd() {
        Instant date = Instant.parse("2025-01-01T01:00:00Z");

        assertDoesNotThrow(
                () -> validator.validateDates(date, date, dateCoverage)
        );
    }

    @Test
    void rejectsMissingStartDate() {
        ConfigurationValidationException error = assertThrows(
                ConfigurationValidationException.class,
                () -> validator.validateDates(
                        null,
                        dateCoverage.end(),
                        dateCoverage
                )
        );

        assertTrue(error.fields().containsKey("rangeStart"));
    }

    @Test
    void rejectsMissingEndDate() {
        ConfigurationValidationException error = assertThrows(
                ConfigurationValidationException.class,
                () -> validator.validateDates(
                        dateCoverage.start(),
                        null,
                        dateCoverage
                )
        );

        assertTrue(error.fields().containsKey("rangeEnd"));
    }

    @Test
    void rejectsStartAfterEnd() {
        Instant start = Instant.parse("2025-01-01T02:00:00Z");
        Instant end = Instant.parse("2025-01-01T01:00:00Z");

        ConfigurationValidationException error = assertThrows(
                ConfigurationValidationException.class,
                () -> validator.validateDates(start, end, dateCoverage)
        );

        assertTrue(error.fields().containsKey("rangeStart"));
    }

    @Test
    void rejectsStartBeforeCoverage() {
        Instant start = Instant.parse("2024-12-31T23:00:00Z");

        ConfigurationValidationException error = assertThrows(
                ConfigurationValidationException.class,
                () -> validator.validateDates(
                        start,
                        dateCoverage.end(),
                        dateCoverage
                )
        );

        assertTrue(error.fields().containsKey("rangeStart"));
    }

    @Test
    void rejectsEndAfterCoverage() {
        Instant end = Instant.parse("2025-01-01T03:00:00Z");

        ConfigurationValidationException error = assertThrows(
                ConfigurationValidationException.class,
                () -> validator.validateDates(
                        dateCoverage.start(),
                        end,
                        dateCoverage
                )
        );

        assertTrue(error.fields().containsKey("rangeEnd"));
    }



}
