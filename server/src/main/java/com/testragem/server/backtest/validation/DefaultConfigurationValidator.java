package com.testragem.server.backtest.validation;

import com.testragem.server.backtest.dto.BacktestRequest;
import com.testragem.server.backtest.model.BacktestConfiguration;
import com.testragem.server.dataset.model.DatasetCoverage;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


/** Checks a request and returns complete settings. No database or strategy calculations are performed here. */
@Component
public class DefaultConfigurationValidator implements ConfigurationValidator {
    private static final Set<String> SUPPORTED_STRATEGIES =
            Set.of("buy-and-hold", "ma-crossover", "rsi");

    @Override public boolean ready() { return true; }

    public record FinancialSettings(BigDecimal initialCapital, BigDecimal feeBps,
            BigDecimal slippageBps, BigDecimal allocationPercent) {}

    /** Reuse the run's financial rules for saved personal defaults, without requiring a dataset. */
    public FinancialSettings validateFinancialSettings(BigDecimal capital, BigDecimal fee,
            BigDecimal slippage, BigDecimal allocation) {
        return new FinancialSettings(validateCapital(capital), validateCost("feeBps", fee),
                validateCost("slippageBps", slippage), validateAllocation(allocation));
    }

    /** Called by BacktestService before the engine receives any settings. */
    @Override public BacktestConfiguration validate(BacktestRequest request, DatasetCoverage coverage) {
        validateDataset(request, coverage);
        String strategy = validateStrategy(request.strategy());

        BigDecimal capital = validateCapital(request.initialCapital());
        BigDecimal fee = validateCost("feeBps", request.feeBps());
        BigDecimal slippage = validateCost("slippageBps", request.slippageBps());
        BigDecimal allocation = validateAllocation(request.allocationPercent());

        validateDates(request.rangeStart(), request.rangeEnd(), coverage);
        Map<String, Integer> parameters = validateParameters(strategy, request.parameters());
        // Warm-up uses the final periods, including any defaults supplied above.
        validateWarmup(strategy, request.rangeStart(), request.rangeEnd(), parameters);

        return new BacktestConfiguration(request.datasetId(), strategy, "1",
                request.rangeStart(), request.rangeEnd(), capital, fee, slippage, allocation, parameters);
    }

    // Package-private helpers are internal to this package and directly accessible to its unit tests.
    BigDecimal validateCapital(BigDecimal value){
         final BigDecimal DEFAULT_CAPITAL = new BigDecimal(10000);
         final BigDecimal MAXIMUM_CAPITAL = new BigDecimal(1000000000);
         // Return the default only when no value is supplied.
         if(value == null){
            return DEFAULT_CAPITAL;
        }
         //checks if value is equal to zero or below zero
         if(value.signum() <= 0){
             throw new ConfigurationValidationException(
                     Map.of("initialCapital", "Capital cannot be zero or negative")
             );
         }

        // Ignore trailing zeros before counting decimal places.
         if(value.stripTrailingZeros().scale() > 8){
             throw new ConfigurationValidationException(
               Map.of("initialCapital", "Use at most eight decimal places.")
             );
         }

         //compares value to maximum capital
        if(value.compareTo(MAXIMUM_CAPITAL) > 0){
            throw new ConfigurationValidationException(
                    Map.of("initialCapital", "Set capital exceeded the maximum capital")
            );
        }

        return value;
    }

    BigDecimal validateCost(String fieldName, BigDecimal value){
        if(value == null){
            return BigDecimal.ZERO;
        }

        if(value.signum() < 0){
            throw new ConfigurationValidationException(
                Map.of(fieldName, "Cost must not be a negative number")
            );
        }

        if(value.compareTo(new BigDecimal("10000")) >= 0){
            throw new ConfigurationValidationException(
              Map.of(fieldName, "Cost must be less than 10000 basis points.")
            );
        }

        if(value.stripTrailingZeros().scale() > 6){
            throw new ConfigurationValidationException(
              Map.of(fieldName, "Use at most six decimal places.")
            );
        }


        return value;
    }

    BigDecimal validateAllocation(BigDecimal value){
        if(value == null){
            return new BigDecimal("100");
        }

        if (value.signum() <= 0) {
            throw new ConfigurationValidationException(
              Map.of("allocationPercent", "Allocation must be greater than zero.")
            );
        }

        if(value.compareTo(new BigDecimal("100")) > 0){
            throw new ConfigurationValidationException(
              Map.of("allocationPercent", "Allocation must not exceed 100.")
            );
        }

        return value;
    }

    void validateDataset(BacktestRequest request, DatasetCoverage coverage){
        if(request == null ||  request.datasetId() == null){
            throw new ConfigurationValidationException(
              Map.of("datasetId", "Select a dataset")
            );
        }


        if(coverage == null || coverage.datasetId() == null
                || coverage.start() == null || coverage.end() == null
                || coverage.start().isAfter(coverage.end()) || coverage.candleCount() < 1){
            throw new ConfigurationValidationException(
                    Map.of("datasetId", "Dataset coverage is unavailable")
            );
        }

        if(!request.datasetId().equals(coverage.datasetId())) {
            throw new ConfigurationValidationException(
                    Map.of("datasetId", "Coverage does not match the selected dataset.")
            );
        }
    }

    void validateDates(Instant start, Instant end, DatasetCoverage coverage){
        if (start == null) {
            throw new ConfigurationValidationException(
                    Map.of("rangeStart", "Start date is required.")
            );
        }

        if (end == null) {
            throw new ConfigurationValidationException(
                    Map.of("rangeEnd", "End date is required.")
            );
        }

        if(start.isAfter(end)){
            throw new ConfigurationValidationException(
              Map.of("rangeStart", "Start date is later than the end date")
            );
        }

        if(start.isBefore(coverage.start())){
            throw new ConfigurationValidationException(
                    Map.of("rangeStart", "Start date is before the dataset coverage")
            );
        }

        if(end.isAfter(coverage.end())){
            throw new ConfigurationValidationException(
                    Map.of("rangeEnd", "End date must not be after dataset coverage.")
            );
        }

        validateUtcAlignment(start, end);
    }

    /** Reject timestamps between UTC hours. An Instant already represents a UTC point in time. */
    void validateUtcAlignment(Instant start, Instant end) {
        validateUtcHour("rangeStart", start);
        validateUtcHour("rangeEnd", end);
    }

    private void validateUtcHour(String fieldName, Instant value) {
        if (value == null) {
            throw new ConfigurationValidationException(
                    Map.of(fieldName, "Date is required."));
        }
        // Check both whole seconds and fractional seconds. floorMod also handles dates before 1970.
        if (value.getNano() != 0 || Math.floorMod(value.getEpochSecond(), 3600L) != 0) {
            throw new ConfigurationValidationException(
                    Map.of(fieldName, "Select an exact UTC hour, without minutes, seconds, or fractional seconds."));
        }
    }

    /** Return the supported key unchanged. Parameter defaults and checks belong to a separate helper. */
    String validateStrategy(String strategy) {
        if (strategy == null || !SUPPORTED_STRATEGIES.contains(strategy)) {
            throw new ConfigurationValidationException(
                    Map.of("strategy", "Select buy-and-hold, ma-crossover, or rsi."));
        }
        return strategy;
    }

    /** Apply defaults to a new map so validation cannot change the caller's parameters. */
    Map<String, Integer> validateParameters(String strategy, Map<String, Integer> supplied) {
        Map<String, Integer> defaults = switch (validateStrategy(strategy)) {
            case "buy-and-hold" -> Map.of();
            case "ma-crossover" -> Map.of("fastPeriod", 10, "slowPeriod", 30);
            case "rsi" -> Map.of("period", 14, "lowerThreshold", 30, "upperThreshold", 70);
            default -> throw new IllegalStateException("Strategy was not validated");
        };
        Map<String, Integer> parameters = new HashMap<>(defaults);
        if (supplied != null) {
            for (var entry : supplied.entrySet()) {
                if (entry.getKey() == null || !defaults.containsKey(entry.getKey())) {
                    throw new ConfigurationValidationException(Map.of("parameters",
                            "Unsupported parameter for " + strategy + ": " + entry.getKey()));
                }
                // A null supported value has the same meaning as an omitted value: use its default.
                if (entry.getValue() != null) parameters.put(entry.getKey(), entry.getValue());
            }
        }

        if (strategy.equals("ma-crossover")) {
            int fast = parameters.get("fastPeriod");
            int slow = parameters.get("slowPeriod");
            if (fast < 2) {
                throw new ConfigurationValidationException(
                        Map.of("parameters.fastPeriod", "Fast period must be at least 2."));
            }
            if (slow < 2) {
                throw new ConfigurationValidationException(
                        Map.of("parameters.slowPeriod", "Slow period must be at least 2."));
            }
            if (fast >= slow) {
                throw new ConfigurationValidationException(
                        Map.of("parameters.fastPeriod", "Fast period must be smaller than the slow period."));
            }
        } else if (strategy.equals("rsi")) {
            int period = parameters.get("period");
            int lower = parameters.get("lowerThreshold");
            int upper = parameters.get("upperThreshold");
            if (period < 2) {
                throw new ConfigurationValidationException(
                        Map.of("parameters.period", "RSI period must be at least 2."));
            }
            if (lower <= 0 || lower >= 100) {
                throw new ConfigurationValidationException(
                        Map.of("parameters.lowerThreshold", "Lower threshold must be between 1 and 99."));
            }
            if (upper <= 0 || upper >= 100) {
                throw new ConfigurationValidationException(
                        Map.of("parameters.upperThreshold", "Upper threshold must be between 1 and 99."));
            }
            if (lower >= upper) {
                throw new ConfigurationValidationException(
                        Map.of("parameters.lowerThreshold", "Lower threshold must be smaller than the upper threshold."));
            }
        }
        return Map.copyOf(parameters);
    }

    /**
     * Check the selected range after dates and strategy parameters have been validated.
     * SMA needs two full averages for a crossing comparison, then a candle for next-open execution.
     * RSI needs its initial value, a later value for comparison, then a candle for execution.
     * This counts expected hourly candles. DatasetService rejects actual gaps before simulation.
     */
    void validateWarmup(String strategy, Instant start, Instant end, Map<String, Integer> parameters) {
        validateStrategy(strategy);
        validateUtcAlignment(start, end);
        if (start.isAfter(end)) {
            throw new ConfigurationValidationException(
                    Map.of("rangeStart", "Start date must not be after the end date."));
        }

        long candleCount = Duration.between(start, end).toHours() + 1;
        long requiredCandles = switch (strategy) {
            case "buy-and-hold" -> 1L;
            case "ma-crossover" -> requiredPeriod(parameters, "slowPeriod") + 2L;
            case "rsi" -> requiredPeriod(parameters, "period") + 3L;
            default -> throw new IllegalStateException("Strategy was not validated");
        };

        if (candleCount < requiredCandles) {
            throw new ConfigurationValidationException(Map.of("rangeEnd",
                    "Select at least " + requiredCandles + " hourly candles for indicator warm-up and next-open execution."));
        }
    }

    // Parameter validation has already applied defaults and range checks.
    private long requiredPeriod(Map<String, Integer> parameters, String key) {
        Integer period = parameters == null ? null : parameters.get(key);
        if (period == null) {
            throw new ConfigurationValidationException(Map.of("parameters." + key,
                    "Supply the validated period before checking historical range length."));
        }
        // Convert before adding the extra candles so a large Integer cannot overflow to a negative minimum.
        return period.longValue();
    }
}
