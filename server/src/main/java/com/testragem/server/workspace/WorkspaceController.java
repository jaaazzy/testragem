package com.testragem.server.workspace;

import static com.testragem.server.workspace.WorkspaceModels.*;
import com.testragem.server.backtest.validation.DefaultConfigurationValidator;
import com.testragem.server.backtest.validation.ConfigurationValidationException;
import com.testragem.server.dataset.service.DatasetService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Saved research belongs to the signed-in user; no owner IDs are accepted from clients. */
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {
    private final WorkspaceRepository repository;
    private final DefaultConfigurationValidator validator;
    private final DatasetService datasets;

    public WorkspaceController(WorkspaceRepository repository, DefaultConfigurationValidator validator, DatasetService datasets) {
        this.repository = repository; this.validator = validator; this.datasets = datasets;
    }

    @GetMapping("/presets")
    public List<Preset> presets(Authentication auth) { return repository.presets(owner(auth)); }

    @GetMapping("/presets/{id}/versions")
    public List<Preset> versions(Authentication auth, @PathVariable UUID id) { return repository.versions(owner(auth), id); }

    @PostMapping("/presets")
    @ResponseStatus(HttpStatus.CREATED)
    public Preset create(Authentication auth, @RequestBody PresetRequest request) { return save(auth, null, request); }

    @PostMapping("/presets/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public Preset revise(Authentication auth, @PathVariable UUID id, @RequestBody PresetRequest request) { return save(auth, id, request); }

    private Preset save(Authentication auth, UUID id, PresetRequest request) {
        String name = text("name", request.name(), 100, true);
        if (request.configuration() == null || request.configuration().datasetId() == null) invalid("datasetId", "Select a dataset.");
        var config = validator.validate(request.configuration(), datasets.coverage(request.configuration().datasetId()));
        if (java.time.Duration.between(config.rangeStart(), config.rangeEnd()).toHours() + 1 > 10000) {
            invalid("rangeEnd", "Presets support up to 10,000 hourly candles per run.");
        }
        return repository.savePreset(owner(auth), id, name, config);
    }

    @GetMapping("/preferences")
    public Preferences preferences(Authentication auth) { return repository.preferences(owner(auth)); }

    @PutMapping("/preferences")
    public Preferences preferences(Authentication auth, @RequestBody Preferences request) {
        if (request.evaluationGoal() == null || !Set.of("return", "drawdown", "costs").contains(request.evaluationGoal())) {
            invalid("evaluationGoal", "Choose return, drawdown, or costs.");
        }
        var financial = validator.validateFinancialSettings(request.initialCapital(), request.feeBps(),
                request.slippageBps(), request.allocationPercent());
        return repository.savePreferences(owner(auth), new Preferences(financial.initialCapital(), financial.feeBps(),
                financial.slippageBps(), financial.allocationPercent(), request.evaluationGoal()));
    }

    @GetMapping("/annotations")
    public List<RunAnnotation> annotations(Authentication auth, @RequestParam List<UUID> ids) {
        if (ids.size() > 50) invalid("ids", "Select at most 50 experiments.");
        return repository.annotations(owner(auth), ids);
    }

    @PutMapping("/annotations/{id}")
    public Annotation annotate(Authentication auth, @PathVariable UUID id, @RequestBody Annotation request) {
        String title = text("title", request.title(), 100, false);
        String notes = text("notes", request.notes(), 4000, false);
        if (request.tags().size() > 8) invalid("tags", "Use at most eight tags.");
        List<String> tags = request.tags().stream().map(tag -> text("tags", tag, 30, true)).distinct().toList();
        return repository.saveAnnotation(owner(auth), id, new Annotation(title, notes, tags));
    }

    private static UUID owner(Authentication auth) { return UUID.fromString(auth.getName()); }
    private static String text(String field, String value, int limit, boolean required) {
        String normalized = value == null ? "" : value.strip();
        if ((required && normalized.isEmpty()) || normalized.length() > limit) {
            invalid(field, "Use " + (required ? "1 to " : "at most ") + limit + " characters.");
        }
        return normalized;
    }
    private static void invalid(String field, String message) {
        throw new ConfigurationValidationException(Map.of(field, message));
    }
}
