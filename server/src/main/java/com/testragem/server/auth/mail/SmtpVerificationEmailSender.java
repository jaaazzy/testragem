package com.testragem.server.auth.mail;

import com.testragem.server.common.error.ApiErrors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.MessagingException;
import org.springframework.stereotype.Component;

/** Sends the same verification code as formatted HTML and a plain-text alternative. */
@Component
public class SmtpVerificationEmailSender implements VerificationEmailSender {
    private final ObjectProvider<JavaMailSender> mail;
    private final String from;

    public SmtpVerificationEmailSender(ObjectProvider<JavaMailSender> mail,
            @Value("${auth.mail.from:}") String from) {
        this.mail = mail; this.from = from;
    }

    @Override
    public void send(String recipient, String code, String verificationPageUrl) {
        JavaMailSender sender = mail.getIfAvailable();
        // Missing SMTP configuration is an explicit failure, not a simulated successful delivery.
        if (sender == null || from.isBlank()) throw unavailable();
        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setSubject("Your Testragem verification code");
            helper.setText(VerificationEmailTemplate.text(code, verificationPageUrl),
                    VerificationEmailTemplate.html(code, verificationPageUrl));
            sender.send(message);
        } catch (MailException | MessagingException ex) {
            // Do not expose provider diagnostics, credentials, or message contents.
            throw unavailable();
        }
    }

    private static ApiErrors.Failure unavailable() {
        return new ApiErrors.Failure(HttpStatus.SERVICE_UNAVAILABLE, "email_delivery_unavailable");
    }
}
