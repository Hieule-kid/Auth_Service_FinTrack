package com.fintrack.auth.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Fallback used when no SMTP host is configured (local development).
 *
 * <p>Logs the link at DEBUG only: a reset link is a credential, so it must not reach INFO-level
 * production logs. Configure {@code spring.mail.host} in every deployed environment.
 */
@Slf4j
@Component
@ConditionalOnExpression("'${spring.mail.host:}'.isBlank()")
public class LoggingPasswordResetMailer implements PasswordResetMailer {

    @Override
    public void sendResetLink(String toEmail, String fullName, String resetLink, long ttlMinutes) {
        log.warn("SMTP is not configured (spring.mail.host); password reset e-mail NOT sent");
        log.debug("[DEV ONLY] password reset link for {}: {}", toEmail, resetLink);
    }
}
