package com.testragem.server.auth.service;

import com.testragem.server.common.error.ApiErrors;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Single-instance, bounded fixed-window limiter. Do not trust forwarded IP headers. */
@Component
public class AuthThrottle {
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final Map<String, Bucket> buckets = new HashMap<>();
    private final Clock clock;
    public AuthThrottle(Clock clock) { this.clock = clock; }

    // Count requests atomically across threads. Limits are stored only in this API process.
    public synchronized void check(String key, int limit) {
        Instant now = clock.instant();
        buckets.entrySet().removeIf(e -> !e.getValue().expires.isAfter(now));
        Bucket bucket = buckets.get(key);
        // Fail closed at capacity, preventing unbounded memory use from rotating identities.
        if (bucket == null) {
            if (buckets.size() >= 10000) throw limited();
            bucket = new Bucket(now.plus(WINDOW));
            buckets.put(key, bucket);
        }
        if (++bucket.count > limit) throw limited();
    }

    private ApiErrors.Failure limited() {
        return new ApiErrors.Failure(HttpStatus.TOO_MANY_REQUESTS, "rate_limited");
    }
    private static class Bucket {
        final Instant expires;
        int count;
        Bucket(Instant expires) { this.expires = expires; }
    }
}
