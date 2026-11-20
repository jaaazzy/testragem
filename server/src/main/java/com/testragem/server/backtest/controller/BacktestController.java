package com.testragem.server.backtest.controller;

import com.testragem.server.auth.service.AuthThrottle;
import com.testragem.server.backtest.dto.BacktestHistory;
import com.testragem.server.backtest.dto.BacktestRequest;
import com.testragem.server.backtest.dto.BacktestResponse;
import com.testragem.server.backtest.service.BacktestService;
import com.testragem.server.dataset.model.CandleWindow;
import java.util.UUID;
import java.util.List;
import com.testragem.server.backtest.validation.ConfigurationValidationException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Receives backtest requests. The signed-in session supplies the owner ID. */
@RestController
@RequestMapping("/api/backtests")
public class BacktestController {
    private final BacktestService backtests;
    private final AuthThrottle throttle;

    public BacktestController(BacktestService backtests, AuthThrottle throttle) {
        this.backtests = backtests;
        this.throttle = throttle;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BacktestResponse run(Authentication auth, @RequestBody BacktestRequest request) {
        throttle.check("backtest-user:" + auth.getName(), 20);
        return backtests.run(UUID.fromString(auth.getName()), request);
    }

    @GetMapping
    public BacktestHistory history(Authentication auth, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return backtests.history(UUID.fromString(auth.getName()), page, size);
    }

    @GetMapping("/{id}")
    public BacktestResponse get(Authentication auth, @PathVariable UUID id) {
        return backtests.get(UUID.fromString(auth.getName()), id);
    }

    @GetMapping("/compare")
    public List<BacktestResponse> compare(Authentication auth, @RequestParam List<UUID> ids) {
        var unique = ids.stream().distinct().toList();
        if (unique.size() < 2 || unique.size() > 4) {
            throw new ConfigurationValidationException(Map.of("ids", "Select two to four different experiments."));
        }
        UUID owner = UUID.fromString(auth.getName());
        // Use the same owner check as a result page. One inaccessible run rejects the request.
        return unique.stream().map(id -> backtests.get(owner, id)).toList();
    }

    @GetMapping("/{id}/chart")
    public CandleWindow chart(Authentication auth, @PathVariable UUID id) {
        return backtests.chart(UUID.fromString(auth.getName()), id);
    }
}
