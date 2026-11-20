package com.testragem.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.web.filter.OncePerRequestFilter;

/** Enforces both idle and absolute expiration, independently of container cleanup. */
public class SessionLifetimeFilter extends OncePerRequestFilter {
    public static final String STARTED = "auth.started";
    public static final String LAST_SEEN = "auth.lastSeen";
    private final Clock clock;
    private final Duration idle;
    private final Duration absolute;

    public SessionLifetimeFilter(Clock clock, Duration idle, Duration absolute) {
        if (idle.isNegative() || idle.isZero() || absolute.isNegative() || absolute.isZero()) {
            throw new IllegalArgumentException("Authentication timeouts must be positive");
        }
        this.clock = clock; this.idle = idle; this.absolute = absolute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var session = request.getSession(false);
        if (session != null && session.getAttribute(STARTED) instanceof Instant started) {
            Instant now = clock.instant();
            Instant lastSeen = (Instant) session.getAttribute(LAST_SEEN);
            if (!now.isBefore(started.plus(absolute)) || lastSeen == null || !now.isBefore(lastSeen.plus(idle))) {
                // End authentication before Spring loads the security context for this request.
                session.invalidate();
            } else {
                // Activity extends the idle window, but cannot extend the absolute session lifetime.
                session.setAttribute(LAST_SEEN, now);
            }
        }
        chain.doFilter(request, response);
    }
}
