package com.testragem.server.auth.service;

import com.testragem.server.auth.mail.VerificationEmailSender;
import com.testragem.server.common.error.ApiErrors;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/** Issues six-digit challenges and accepts an unexpired code only once. Stored codes are hashed. */
@Service
public class EmailVerificationService {
    private static final Duration LIFETIME = Duration.ofMinutes(10);
    private final JdbcClient jdbc;
    private final Clock clock;
    private final VerificationEmailSender sender;
    private final String publicUrl;
    private final SecureRandom random = new SecureRandom();
    private final PasswordEncoder codes;

    public EmailVerificationService(JdbcClient jdbc, Clock clock, VerificationEmailSender sender, PasswordEncoder codes,
            @Value("${auth.public-url}") String publicUrl) {
        URI uri = URI.create(publicUrl);
        boolean local = "http".equals(uri.getScheme()) && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        if ((!"https".equals(uri.getScheme()) && !local) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("AUTH_PUBLIC_URL must be an HTTPS frontend URL (HTTP allowed only for localhost)");
        }
        this.jdbc = jdbc; this.clock = clock; this.sender = sender;
        this.codes = codes;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    /** Joins registration's transaction: a failed send rolls back account creation. */
    @Transactional
    public void issue(UUID userId, String email) {
        // Lock the user first for a consistent lock order with verification/resend.
        jdbc.sql("SELECT id FROM users WHERE id = :id FOR UPDATE").param("id", userId).query(UUID.class).single();
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        // Do not reissue the exact same six digits when replacing a pending code.
        var previous = jdbc.sql("SELECT code_hash FROM email_verification_codes WHERE user_id = :id")
                .param("id", userId).query(String.class).optional();
        while (previous.isPresent() && codes.matches(code, previous.get())) {
            code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        }
        jdbc.sql("DELETE FROM email_verification_codes WHERE user_id = :id").param("id", userId).update();
        jdbc.sql("INSERT INTO email_verification_codes(user_id, code_hash, expires_at) VALUES (:id, :hash, :expires)")
                .param("id", userId).param("hash", codes.encode(code)).param("expires", now().plus(LIFETIME)).update();
        sender.send(email, code, publicUrl + "/#/verify-email");
    }

    @Transactional
    public void resend(String email) {
        String normalized = AuthService.normalizeEmail(email);
        var user = jdbc.sql("SELECT id FROM users WHERE email = :email AND email_verified_at IS NULL AND password_hash IS NOT NULL FOR UPDATE")
                .param("email", normalized).query(UUID.class).optional();
        // Same successful response for unknown, already verified and pending accounts.
        user.ifPresent(id -> issue(id, normalized));
    }

    // Failed guesses must commit their attempt decrement, rather than roll it back.
    @Transactional(noRollbackFor = ApiErrors.Failure.class)
    public void verify(String email, String code) {
        if (code == null || !code.matches("[0-9]{6}")) throw invalidCode();
        String normalized;
        try { normalized = AuthService.normalizeEmail(email); }
        catch (ApiErrors.Failure ex) { throw invalidCode(); }
        var userId = jdbc.sql("SELECT id FROM users WHERE email = :email AND email_verified_at IS NULL FOR UPDATE")
                .param("email", normalized).query(UUID.class).optional().orElseThrow(EmailVerificationService::invalidCode);
        var challenge = jdbc.sql("SELECT code_hash, expires_at, remaining_attempts FROM email_verification_codes WHERE user_id = :id")
                .param("id", userId).query(Challenge.class).optional().orElseThrow(EmailVerificationService::invalidCode);
        if (!challenge.expiresAt().isAfter(now()) || challenge.remainingAttempts() == 0) throw invalidCode();
        if (!codes.matches(code, challenge.codeHash())) {
            jdbc.sql("UPDATE email_verification_codes SET remaining_attempts = remaining_attempts - 1 WHERE user_id = :id")
                    .param("id", userId).update();
            throw invalidCode();
        }
        jdbc.sql("DELETE FROM email_verification_codes WHERE user_id = :id").param("id", userId).update();
        // Consume the challenge and mark the account verified in the same transaction.
        jdbc.sql("UPDATE users SET email_verified_at = :now WHERE id = :id")
                .param("id", userId).param("now", now()).update();
    }

    private OffsetDateTime now() { return clock.instant().atOffset(ZoneOffset.UTC); }

    private record Challenge(String codeHash, OffsetDateTime expiresAt, int remainingAttempts) {}

    private static ApiErrors.Failure invalidCode() {
        return new ApiErrors.Failure(HttpStatus.BAD_REQUEST, "invalid_or_expired_verification");
    }
}
