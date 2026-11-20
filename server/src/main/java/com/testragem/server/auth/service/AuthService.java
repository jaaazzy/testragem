package com.testragem.server.auth.service;

import com.testragem.server.common.error.ApiErrors;
import com.testragem.server.user.service.UserService;

import com.testragem.server.user.entity.User;
import com.testragem.server.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates account input, hashes passwords, and requires email verification before sign-in. */
@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final String dummyHash;
    private final EmailVerificationService verification;
    private final UserService identities;

    public AuthService(UserRepository users, PasswordEncoder passwords, EmailVerificationService verification, UserService identities) {
        this.users = users; this.passwords = passwords;
        this.verification = verification;
        this.identities = identities;
        dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public static String normalizeEmail(String email) {
        if (email == null) throw invalid();
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        // Deliberate MVP policy: ASCII addresses with a DNS-style dotted domain.
        // No provider-specific dot removal or plus-address rewriting.
        if (normalized.length() > 254 || !normalized.matches(
                "[a-z0-9!#$%&'*+/=?^_`{|}~.-]{1,64}@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+")
                || normalized.startsWith(".") || normalized.contains("..") || normalized.contains(".@")) throw invalid();
        return normalized;
    }

    // BCrypt accepts at most 72 UTF-8 bytes. Reject longer input instead of silently truncating it.
    private static boolean acceptablePassword(String password) {
        return password != null && password.codePointCount(0, password.length()) >= 12
                && password.getBytes(StandardCharsets.UTF_8).length <= 72
                && password.indexOf('\0') < 0;
    }

    // Store the account and challenge in one transaction. Never save the plaintext password.
    @Transactional
    public User register(String email, String displayName, String password) {
        String normalized = normalizeEmail(email);
        if (!acceptablePassword(password) || displayName == null || displayName.strip().isEmpty()
                || displayName.strip().length() > 150 || displayName.chars().anyMatch(Character::isISOControl)) throw invalid();
        try {
            User user = users.create(normalized, displayName.strip(), passwords.encode(password));
            verification.issue(user.id(), normalized);
            return user;
        } catch (DuplicateKeyException ex) {
            throw new ApiErrors.Failure(HttpStatus.CONFLICT, "email_already_registered");
        }
    }

    public User login(String email, String password) {
        String normalized;
        try { normalized = normalizeEmail(email); }
        catch (ApiErrors.Failure ex) { normalized = ""; }
        var credentials = users.findCredentials(normalized);
        String hash = credentials.map(UserRepository.Credentials::passwordHash).orElse(dummyHash);
        // Still perform a hash check for unknown, legacy, or malformed credentials.
        boolean valid = passwords.matches(acceptablePassword(password) ? password : "", hash);
        if (!valid || !acceptablePassword(password) || credentials.isEmpty()
                || credentials.get().passwordHash() == null) {
            throw new ApiErrors.Failure(HttpStatus.UNAUTHORIZED, "invalid_credentials");
        }
        if (!credentials.get().emailVerified()) {
            throw new ApiErrors.Failure(HttpStatus.FORBIDDEN, "email_not_verified");
        }
        return identities.getById(credentials.get().id());
    }

    private static ApiErrors.Failure invalid() {
        return new ApiErrors.Failure(HttpStatus.BAD_REQUEST, "invalid_request");
    }
}
