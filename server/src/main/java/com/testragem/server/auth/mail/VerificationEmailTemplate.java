package com.testragem.server.auth.mail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.util.HtmlUtils;

/** Shared by SMTP delivery and previews; no scripts, external fonts or tracking. */
public final class VerificationEmailTemplate {
    private static final String TEMPLATE = load();
    private VerificationEmailTemplate() {}

    public static String html(String code, String verificationPageUrl) {
        if (code == null || !code.matches("[0-9]{6}")) throw new IllegalArgumentException("A six-digit code is required");
        return TEMPLATE.replace("{{code}}", code)
                .replace("{{verificationUrl}}", HtmlUtils.htmlEscape(verificationPageUrl, "UTF-8"));
    }

    public static String text(String code, String verificationPageUrl) {
        return "Verify your Testragem email\n\nYour verification code is: " + code
                + "\n\nEnter it on " + verificationPageUrl
                + "\n\nThis code expires in 10 minutes and works once. Never share it."
                + "\nIf you did not create an account, you can ignore this email.\n";
    }

    private static String load() {
        try {
            return new ClassPathResource("mail/verify-email.html").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) { throw new IllegalStateException("Verification email template is missing", ex); }
    }
}
