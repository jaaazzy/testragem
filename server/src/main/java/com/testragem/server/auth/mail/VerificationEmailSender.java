package com.testragem.server.auth.mail;

/** Delivery boundary: production uses SMTP; tests use an in-memory mailbox. */
public interface VerificationEmailSender {
    void send(String recipient, String code, String verificationPageUrl);
}
