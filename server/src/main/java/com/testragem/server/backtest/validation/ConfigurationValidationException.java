package com.testragem.server.backtest.validation;
import java.util.Map;

/** Module 1 can report field paths such as parameters.fastPeriod or rangeEnd. */
public class ConfigurationValidationException extends RuntimeException {
    private final Map<String,String> fields;
    public ConfigurationValidationException(Map<String,String> fields) {
        super("invalid_configuration"); this.fields=Map.copyOf(fields);
    }
    public Map<String,String> fields() { return fields; }
}
