package com.testragem.server.auth.controller;

import com.testragem.server.auth.service.AuthService;
import com.testragem.server.auth.service.AuthThrottle;
import com.testragem.server.auth.service.EmailVerificationService;
import com.testragem.server.common.error.ApiErrors;
import com.testragem.server.security.SessionLifetimeFilter;

import com.testragem.server.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

/** Handles account requests and saves successful authentication in a server-managed session. */
@RestController
public class AuthController {
    private final AuthService service;
    private final AuthThrottle throttle;
    private final Clock clock;
    private final HttpSessionSecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessions;
    private final EmailVerificationService verification;

    public AuthController(AuthService service, AuthThrottle throttle, Clock clock,
            HttpSessionSecurityContextRepository contexts, SessionAuthenticationStrategy sessions,
            EmailVerificationService verification) {
        this.service = service; this.throttle = throttle; this.clock = clock;
        this.contexts = contexts; this.sessions = sessions;
        this.verification = verification;
    }

    // The browser retrieves this token before a state-changing request, including login.
    @GetMapping("/api/auth/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    User register(@RequestBody Registration input, HttpServletRequest request) {
        throttle.check("register:" + request.getRemoteAddr(), 20);
        return service.register(input.email(), input.displayName(), input.password());
    }

    @PostMapping("/api/auth/login")
    User login(@RequestBody Login input, HttpServletRequest request, HttpServletResponse response) {
        throttle.check("login-ip:" + request.getRemoteAddr(), 50);
        String email = input.email() == null ? "" : input.email().strip().toLowerCase(Locale.ROOT);
        // Cap retained key length even for invalid input.
        throttle.check("login-email:" + email.substring(0, Math.min(email.length(), 254)), 10);
        User user = service.login(input.email(), input.password());
        // Use the stable user UUID as the principal. Private APIs derive ownership from this value.
        var auth = UsernamePasswordAuthenticationToken.authenticated(user.id().toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        // Rotate the session ID and CSRF token so pre-login credentials cannot be reused.
        sessions.onAuthentication(auth, request, response);
        var session = request.getSession(true);
        session.setAttribute(SessionLifetimeFilter.STARTED, clock.instant());
        session.setAttribute(SessionLifetimeFilter.LAST_SEEN, clock.instant());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        // Save the context so later requests can identify the user from the session cookie.
        contexts.saveContext(context, request, response);
        return user;
    }

    @PostMapping("/api/auth/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void verifyEmail(@RequestBody Verification input, HttpServletRequest request) {
        throttle.check("verify-ip:" + request.getRemoteAddr(), 50);
        String email;
        try { email = AuthService.normalizeEmail(input.email()); }
        catch (ApiErrors.Failure ex) { throw new ApiErrors.Failure(HttpStatus.BAD_REQUEST, "invalid_or_expired_verification"); }
        throttle.check("verify-email:" + email, 10);
        verification.verify(email, input.code());
    }

    @PostMapping("/api/auth/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Map<String, String> resendVerification(@RequestBody Email input, HttpServletRequest request) {
        throttle.check("resend-ip:" + request.getRemoteAddr(), 20);
        String email = AuthService.normalizeEmail(input.email());
        throttle.check("resend-email:" + email, 3);
        verification.resend(email);
        return Map.of("message", "If this account needs verification, an email has been sent.");
    }

    public record Registration(String email, String displayName, String password) {}
    public record Login(String email, String password) {}
    public record Verification(String email, String code) {}
    public record Email(String email) {}
}
