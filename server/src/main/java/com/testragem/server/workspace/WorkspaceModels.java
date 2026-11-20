package com.testragem.server.workspace;

import com.testragem.server.backtest.dto.BacktestRequest;
import com.testragem.server.backtest.model.BacktestConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WorkspaceModels {
    private WorkspaceModels() {}

    public record PresetRequest(String name, BacktestRequest configuration) {}
    public record Preset(UUID id, int version, String name, BacktestConfiguration configuration, Instant createdAt) {}
    public record Preferences(BigDecimal initialCapital, BigDecimal feeBps, BigDecimal slippageBps,
            BigDecimal allocationPercent, String evaluationGoal) {
        public static Preferences defaults() {
            return new Preferences(new BigDecimal("10000"), BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("100"), "return");
        }
    }
    public record Annotation(String title, String notes, List<String> tags) {
        public Annotation { tags = tags == null ? List.of() : List.copyOf(tags); }
        public static Annotation empty() { return new Annotation("", "", List.of()); }
    }
    public record RunAnnotation(UUID id, Annotation annotation) {}
}
