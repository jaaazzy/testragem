package com.testragem.server;

import com.jayway.jsonpath.JsonPath;
import com.testragem.server.auth.service.AuthThrottle;
import com.testragem.server.common.error.ApiErrors;
import com.testragem.server.auth.mail.VerificationEmailSender;
import com.testragem.server.auth.mail.SmtpVerificationEmailSender;
import com.testragem.server.auth.mail.VerificationEmailTemplate;
import jakarta.mail.Part;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.MailSendException;
import java.util.concurrent.atomic.AtomicReference;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

/** Real HTTP requests through the embedded servlet container, security chain and JDBC. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(AuthenticationIntegrationTest.TimeConfig.class)
class AuthenticationIntegrationTest {
    private static final String PASSWORD = "Correct horse battery staple!";
    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired MutableClock clock;
    @Autowired AuthThrottle throttle;
    @Autowired TestMailbox mailbox;

    @BeforeEach void nextWindow() { clock.advance(Duration.ofDays(1)); mailbox.failNext.set(false); }

    @Test void registrationLoginIdentityAndLogout() throws Exception {
        Browser browser = new Browser();
        HttpResponse<String> csrf = browser.csrf();
        assertThat(csrf.headers().allValues("set-cookie")).anySatisfy(cookie -> {
            assertThat(cookie).contains("TESTRAGEM_SESSION=").contains("HttpOnly").contains("Secure").contains("SameSite=Lax");
            assertThat(cookie).doesNotContain("Domain=");
        });
        String email = email();
        var registered = browser.register("  " + email.toUpperCase() + "  ", PASSWORD);
        assertThat(registered.statusCode()).isEqualTo(201);
        assertThat((String) JsonPath.read(registered.body(), "$.email")).isEqualTo(email);
        assertThat(registered.body()).doesNotContain("password", "hash", PASSWORD);
        assertThat(browser.get("/api/users/me").statusCode()).isEqualTo(401);
        String hash = jdbc.sql("SELECT password_hash FROM users WHERE email = :email").param("email", email).query(String.class).single();
        assertThat(hash).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(passwords.matches(PASSWORD, hash)).isTrue();
        browser.verify(email);
        String oldSession = browser.cookies.get("TESTRAGEM_SESSION");
        String oldCsrf = browser.token;
        var login = browser.login(email.toUpperCase(), PASSWORD);
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(browser.cookies.get("TESTRAGEM_SESSION")).isNotEqualTo(oldSession);
        assertThat(browser.post("/api/auth/logout", "{}").statusCode()).isEqualTo(403); // old CSRF rotated
        Browser replay = new Browser();
        replay.cookies.put("TESTRAGEM_SESSION", oldSession);
        assertThat(replay.get("/api/users/me").statusCode()).isEqualTo(401);
        var me = browser.get("/api/users/me");
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(me.body(), "$.id")).isEqualTo(JsonPath.read(registered.body(), "$.id"));
        browser.csrf();
        assertThat(browser.token).isNotEqualTo(oldCsrf);
        String signedInSession = browser.cookies.get("TESTRAGEM_SESSION");
        assertThat(browser.post("/api/auth/logout", "{}").statusCode()).isEqualTo(204);
        assertThat(browser.get("/api/users/me").statusCode()).isEqualTo(401);
        replay.cookies.put("TESTRAGEM_SESSION", signedInSession);
        assertThat(replay.get("/api/users/me").statusCode()).isEqualTo(401);
    }

    @Test void duplicateEmailUsesNormalizationAndDatabaseUniqueness() throws Exception {
        Browser b = new Browser(); b.csrf();
        String email = email();
        assertThat(b.register(email, PASSWORD).statusCode()).isEqualTo(201);
        var duplicate = b.register(" " + email.toUpperCase() + " ", PASSWORD);
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(duplicate.body()).contains("email_already_registered").doesNotContain("SQL", "password_hash");
        assertThat(jdbc.sql("SELECT count(*) FROM users WHERE email = :email").param("email", email).query(Integer.class).single()).isEqualTo(1);
    }

    @Test void invalidInputAndMalformedJsonAreRejected() throws Exception {
        Browser b = new Browser(); b.csrf();
        for (String email : new String[]{"", "no-at-sign", "a..b@example.com", "é@example.com"}) {
            assertThat(b.register(email, PASSWORD).statusCode()).isEqualTo(400);
        }
        assertThat(b.register(email(), "short").statusCode()).isEqualTo(400);
        assertThat(b.register(email(), "a".repeat(73)).statusCode()).isEqualTo(400);
        assertThat(b.register(email(), "é".repeat(37)).statusCode()).isEqualTo(400);
        assertThat(b.post("/api/auth/register", "{\"email\":null}").statusCode()).isEqualTo(400);
        assertThat(b.post("/api/auth/register", "{broken").body()).contains("invalid_request");
        assertThat(b.post("/api/auth/register", "{\"email\":\"x@example.com\",\"displayName\":\" \",\"password\":\"" + PASSWORD + "\"}").statusCode()).isEqualTo(400);
    }

    @Test void badPasswordsUnknownAccountsAndLegacyUsersHaveSameError() throws Exception {
        Browser b = new Browser(); b.csrf();
        String email = email(); b.register(email, PASSWORD);
        String legacyEmail = email();
        jdbc.sql("INSERT INTO users(id, email, display_name) VALUES (:id, :email, 'Legacy')")
                .param("id", UUID.randomUUID()).param("email", legacyEmail).update();
        for (String address : new String[]{email, email(), legacyEmail, "invalid"}) {
            var response = b.login(address, "wrong password here");
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.body()).isEqualTo("{\"error\":\"invalid_credentials\"}");
        }
        assertThat(b.get("/api/users/me").statusCode()).isEqualTo(401);
    }

    @Test void csrfAndRouteProtectionAreEnforced() throws Exception {
        Browser b = new Browser();
        assertThat(b.register(email(), PASSWORD).statusCode()).isEqualTo(403);
        assertThat(b.login(email(), PASSWORD).statusCode()).isEqualTo(403);
        assertThat(b.post("/api/auth/logout", "{}").statusCode()).isEqualTo(403);
        assertThat(b.get("/api/users/me").body()).isEqualTo("{\"error\":\"unauthenticated\"}");
        assertThat(b.get("/api/private/future").statusCode()).isEqualTo(401);
        assertThat(b.get("/actuator/info").statusCode()).isEqualTo(401);
        assertThat(b.get("/api/health").statusCode()).isEqualTo(200);
        b.cookies.put("TESTRAGEM_SESSION", "forged-or-expired-session");
        assertThat(b.get("/api/users/me").statusCode()).isEqualTo(401);
        b.csrf(); b.token = "invalid";
        assertThat(b.register(email(), PASSWORD).body()).isEqualTo("{\"error\":\"forbidden\"}");
    }

    @Test void idleExpiryRequiresNewCsrfAndReauthentication() throws Exception {
        Browser b = signedIn();
        clock.advance(Duration.ofMinutes(30));
        assertThat(b.get("/api/users/me").statusCode()).isEqualTo(401);
        assertThat(b.post("/api/auth/logout", "{}").statusCode()).isEqualTo(403);
        b.csrf();
        assertThat(b.login(b.email, PASSWORD).statusCode()).isEqualTo(200);
        assertThat(b.get("/api/users/me").statusCode()).isEqualTo(200);
    }

    @Test void absoluteExpiryCannotBeExtendedByActivity() throws Exception {
        Browser b = signedIn();
        for (int i = 0; i < 47; i++) {
            clock.advance(Duration.ofMinutes(10));
            assertThat(b.get("/api/users/me").statusCode()).isEqualTo(200);
        }
        clock.advance(Duration.ofMinutes(10));
        assertThat(b.get("/api/users/me").statusCode()).isEqualTo(401);
    }

    @Test void loginThrottleSurvivesNewSessionsAndResetsAfterWindow() throws Exception {
        String email = email();
        for (int i = 0; i < 10; i++) {
            Browser b = new Browser(); b.csrf();
            assertThat(b.login(email, PASSWORD).statusCode()).isEqualTo(401);
        }
        Browser b = new Browser(); b.csrf();
        var limited = b.login(email.toUpperCase(), PASSWORD);
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.headers().firstValue("Retry-After")).contains("900");
        clock.advance(Duration.ofMinutes(15));
        assertThat(b.login(email, PASSWORD).statusCode()).isEqualTo(401);
    }

    @Test void ipThrottleAndRegistrationThrottleApplyAtConfiguredLimits() {
        String key = UUID.randomUUID().toString();
        for (int i = 0; i < 50; i++) throttle.check("login-ip:" + key, 50);
        assertThatThrownBy(() -> throttle.check("login-ip:" + key, 50)).isInstanceOf(ApiErrors.Failure.class);
        for (int i = 0; i < 20; i++) throttle.check("register:" + key, 20);
        assertThatThrownBy(() -> throttle.check("register:" + key, 20)).isInstanceOf(ApiErrors.Failure.class);
    }

    @Test void verificationRequiredBeforeLoginAndCodeStoredOnlyAsHash() throws Exception {
        Browser b = new Browser(); b.csrf(); String email = email();
        var registered = b.register(email, PASSWORD);
        assertThat(registered.statusCode()).isEqualTo(201);
        String code = mailbox.code(email);
        assertThat(code).matches("[0-9]{6}");
        assertThat(registered.body()).doesNotContain(code, "token", "code");
        String hash = jdbc.sql("SELECT code_hash FROM email_verification_codes WHERE user_id = (SELECT id FROM users WHERE email = :email)")
                .param("email", email).query(String.class).single();
        assertThat(hash).startsWith("$2").isNotEqualTo(code);
        assertThat(passwords.matches(code, hash)).isTrue();
        assertThat(b.login(email, PASSWORD).statusCode()).isEqualTo(403);
        assertThat(b.login(email, PASSWORD).body()).contains("email_not_verified");
        assertThat(b.get("/api/users/me").statusCode()).isEqualTo(401);
        b.verify(email);
        assertThat(b.get("/api/users/me").statusCode()).isEqualTo(401); // verification does not sign in
        assertThat(b.verifyResponse(email, code).statusCode()).isEqualTo(400);
        assertThat(b.login(email, PASSWORD).statusCode()).isEqualTo(200);
    }

    @Test void expiredInvalidAndMissingVerificationCodesAreRejected() throws Exception {
        Browser b = new Browser(); b.csrf(); String email = email(); b.register(email, PASSWORD);
        clock.advance(Duration.ofMinutes(10));
        assertThat(b.verifyResponse(email, mailbox.code(email)).statusCode()).isEqualTo(400);
        for (String body : new String[]{"{}", "{\"email\":null,\"code\":null}", "{\"email\":\"invalid\",\"code\":\"123456\"}"}) {
            assertThat(b.post("/api/auth/verify-email", body).statusCode()).isEqualTo(400);
        }
        for (String code : new String[]{"", "12345", "1234567", "12a456"}) assertThat(b.verifyResponse(email, code).statusCode()).isEqualTo(400);
        assertThat(b.login(email, PASSWORD).body()).contains("email_not_verified");
    }

    @Test void resendReplacesOldCodeAndHidesAccountStatus() throws Exception {
        Browser b = new Browser(); b.csrf(); String email = email(); b.register(email, PASSWORD);
        String oldCode = mailbox.code(email);
        var response = b.post("/api/auth/resend-verification", "{\"email\":\" " + email.toUpperCase() + " \"}");
        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(mailbox.code(email)).isNotEqualTo(oldCode);
        assertThat(b.verifyResponse(email, oldCode).statusCode()).isEqualTo(400);
        b.verify(email);
        String latestCode = mailbox.codes.get(email);
        assertThat(b.post("/api/auth/resend-verification", "{\"email\":\"" + email + "\"}").body()).isEqualTo(response.body());
        assertThat(mailbox.codes.get(email)).isEqualTo(latestCode);
        String unknown = email();
        assertThat(b.post("/api/auth/resend-verification", "{\"email\":\"" + unknown + "\"}").body()).isEqualTo(response.body());
        assertThat(mailbox.codes).doesNotContainKey(unknown);
    }

    @Test void emailFailureRollsBackRegistrationAndPreservesOldCodeOnResend() throws Exception {
        Browser b = new Browser(); b.csrf(); String email = email();
        mailbox.failNext.set(true);
        var failed = b.register(email, PASSWORD);
        assertThat(failed.statusCode()).isEqualTo(503);
        assertThat(failed.body()).isEqualTo("{\"error\":\"email_delivery_unavailable\"}");
        assertThat(jdbc.sql("SELECT count(*) FROM users WHERE email = :email").param("email", email).query(Integer.class).single()).isZero();
        assertThat(b.register(email, PASSWORD).statusCode()).isEqualTo(201);
        String oldCode = mailbox.code(email);
        mailbox.failNext.set(true);
        assertThat(b.post("/api/auth/resend-verification", "{\"email\":\"" + email + "\"}").statusCode()).isEqualTo(503);
        assertThat(b.verifyResponse(email, oldCode).statusCode()).isEqualTo(204);
    }

    @Test void resendIsCsrfProtectedAndThrottledAcrossSessions() throws Exception {
        Browser b = new Browser();
        assertThat(b.post("/api/auth/resend-verification", "{\"email\":\"" + email() + "\"}").statusCode()).isEqualTo(403);
        assertThat(b.verifyResponse(email(), "123456").statusCode()).isEqualTo(403);
        String email = email();
        for (int i = 0; i < 3; i++) {
            b = new Browser(); b.csrf();
            assertThat(b.post("/api/auth/resend-verification", "{\"email\":\"" + email + "\"}").statusCode()).isEqualTo(202);
        }
        assertThat(b.post("/api/auth/resend-verification", "{\"email\":\"" + email.toUpperCase() + "\"}").statusCode()).isEqualTo(429);
    }

    @Test void concurrentVerificationCanOnlyConsumeCodeOnce() throws Exception {
        Browser b = new Browser(); b.csrf(); String email = email(); b.register(email, PASSWORD);
        Browser other = new Browser(); other.csrf();
        String body = "{\"email\":\"" + email + "\",\"code\":\"" + mailbox.code(email) + "\"}";
        var one = CompletableFuture.supplyAsync(() -> {
            try { return b.post("/api/auth/verify-email", body).statusCode(); }
            catch (Exception ex) { throw new RuntimeException(ex); }
        });
        var two = CompletableFuture.supplyAsync(() -> {
            try { return other.post("/api/auth/verify-email", body).statusCode(); }
            catch (Exception ex) { throw new RuntimeException(ex); }
        });
        assertThat(java.util.List.of(one.get(), two.get())).containsExactlyInAnyOrder(204, 400);
    }

    @Test void fiveIncorrectGuessesPersistAcrossSessionsAndRequireResend() throws Exception {
        Browser b = new Browser(); b.csrf(); String email = email(); b.register(email, PASSWORD);
        String rightCode = mailbox.code(email);
        String wrongCode = "000000".equals(rightCode) ? "000001" : "000000";
        for (int i = 0; i < 5; i++) {
            b = new Browser(); b.csrf();
            assertThat(b.verifyResponse(email, wrongCode).statusCode()).isEqualTo(400);
            assertThat(jdbc.sql("SELECT remaining_attempts FROM email_verification_codes WHERE user_id = (SELECT id FROM users WHERE email = :email)")
                    .param("email", email).query(Integer.class).single()).isEqualTo(4 - i);
        }
        assertThat(b.verifyResponse(email, rightCode).statusCode()).isEqualTo(400);
        assertThat(b.post("/api/auth/resend-verification", "{\"email\":\"" + email + "\"}").statusCode()).isEqualTo(202);
        b.verify(email);
    }

    @Test void codesAreBoundToEmailAndSupportLeadingZeroes() throws Exception {
        Browser b = new Browser(); b.csrf(); String email = email(); b.register(email, PASSWORD);
        jdbc.sql("UPDATE email_verification_codes SET code_hash = :hash WHERE user_id = (SELECT id FROM users WHERE email = :email)")
                .param("hash", passwords.encode("012345")).param("email", email).update();
        assertThat(b.verifyResponse(email(), "012345").statusCode()).isEqualTo(400);
        assertThat(b.verifyResponse(" " + email.toUpperCase() + " ", "012345").statusCode()).isEqualTo(204);
    }

    @Test void smtpEmailContainsHtmlAndTextAlternativesWithSixDigitCode() throws Exception {
        AtomicReference<MimeMessage> captured = new AtomicReference<>();
        JavaMailSenderImpl sender = new JavaMailSenderImpl() {
            @Override public void send(MimeMessage message) { captured.set(message); }
        };
        var beans = new StaticListableBeanFactory(); beans.addBean("mail", sender);
        var delivery = new SmtpVerificationEmailSender(beans.getBeanProvider(JavaMailSender.class), "no-reply@example.com");
        delivery.send("recipient@example.com", "012345", "https://testragem.example/#/verify-email");
        MimeMessage message = captured.get();
        assertThat(message).isNotNull(); message.saveChanges();
        assertThat(message.getSubject()).isEqualTo("Your Testragem verification code");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("recipient@example.com");
        String html = mimeBody(message, "text/html");
        String plain = mimeBody(message, "text/plain");
        assertThat(html).contains("012345", "Verify your email.", "#4c59a8", "10 minutes", "Open verification page").doesNotContain("{{", "<script", "<img");
        assertThat(plain).contains("012345", "10 minutes", "https://testragem.example/#/verify-email");
        assertThat(VerificationEmailTemplate.html("012345", "https://testragem.example/?a=1&b=2")).contains("a=1&amp;b=2");
    }

    @Test void smtpFailuresAndMissingConfigurationDoNotExposeProviderDiagnostics() {
        var beans = new StaticListableBeanFactory();
        var missing = new SmtpVerificationEmailSender(beans.getBeanProvider(JavaMailSender.class), "");
        assertThatThrownBy(() -> missing.send("recipient@example.com", "012345", "https://testragem.example"))
                .isInstanceOf(ApiErrors.Failure.class).hasMessage("email_delivery_unavailable");
        beans.addBean("mail", new JavaMailSenderImpl() {
            @Override public void send(MimeMessage message) { throw new MailSendException("private-provider-diagnostic"); }
        });
        var failing = new SmtpVerificationEmailSender(beans.getBeanProvider(JavaMailSender.class), "no-reply@example.com");
        assertThatThrownBy(() -> failing.send("recipient@example.com", "012345", "https://testragem.example"))
                .isInstanceOf(ApiErrors.Failure.class).hasMessage("email_delivery_unavailable");
    }

    private static String mimeBody(Part part, String contentType) throws Exception {
        if (part.isMimeType(contentType)) return (String) part.getContent();
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String body = mimeBody(multipart.getBodyPart(i), contentType);
                if (body != null) return body;
            }
        }
        return null;
    }

    private String email() { return UUID.randomUUID() + "@example.com"; }
    private Browser signedIn() throws Exception {
        Browser b = new Browser(); b.csrf(); b.email = email();
        assertThat(b.register(b.email, PASSWORD).statusCode()).isEqualTo(201);
        b.verify(b.email);
        assertThat(b.login(b.email, PASSWORD).statusCode()).isEqualTo(200);
        b.csrf(); return b;
    }

    class Browser {
        final HttpClient client = HttpClient.newHttpClient();
        final Map<String, String> cookies = new LinkedHashMap<>();
        String token;
        String email;
        HttpResponse<String> csrf() throws Exception {
            var result = get("/api/auth/csrf");
            assertThat(result.statusCode()).isEqualTo(200);
            token = JsonPath.read(result.body(), "$.token"); return result;
        }
        HttpResponse<String> get(String path) throws Exception { return send("GET", path, null); }
        HttpResponse<String> post(String path, String body) throws Exception { return send("POST", path, body); }
        HttpResponse<String> register(String email, String password) throws Exception {
            return post("/api/auth/register", "{\"email\":\"" + email + "\",\"displayName\":\" Test User \",\"password\":\"" + password + "\"}");
        }
        HttpResponse<String> login(String email, String password) throws Exception {
            return post("/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
        }
        void verify(String email) throws Exception {
            assertThat(verifyResponse(email, mailbox.code(email)).statusCode()).isEqualTo(204);
        }
        HttpResponse<String> verifyResponse(String email, String code) throws Exception {
            return post("/api/auth/verify-email", "{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}");
        }
        HttpResponse<String> send(String method, String path, String body) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json");
            if (!cookies.isEmpty()) builder.header("Cookie", String.join("; ", cookies.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue()).toList()));
            if (token != null) builder.header("X-CSRF-TOKEN", token);
            builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            for (String cookie : response.headers().allValues("set-cookie")) {
                String[] parts = cookie.split(";", 2)[0].split("=", 2);
                if (parts.length == 2) cookies.put(parts[0], parts[1]);
            }
            return response;
        }
    }

    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
        @Bean @Primary TestMailbox testMailbox() { return new TestMailbox(); }
    }
    static class TestMailbox implements VerificationEmailSender {
        final Map<String, String> codes = new ConcurrentHashMap<>();
        final AtomicBoolean failNext = new AtomicBoolean();
        @Override public void send(String recipient, String code, String url) {
            if (failNext.getAndSet(false)) throw new ApiErrors.Failure(HttpStatus.SERVICE_UNAVAILABLE, "email_delivery_unavailable");
            codes.put(recipient, code);
        }
        String code(String email) {
            String code = codes.get(email);
            assertThat(code).as("verification email was delivered").isNotNull();
            return code;
        }
    }
    static class MutableClock extends Clock {
        private volatile Instant now = Instant.parse("2026-01-01T00:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
