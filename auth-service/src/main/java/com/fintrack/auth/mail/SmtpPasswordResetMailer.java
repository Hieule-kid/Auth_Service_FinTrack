package com.fintrack.auth.mail;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;

/**
 * SMTP-backed mailer, active when {@code spring.mail.host} is configured.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("!'${spring.mail.host:}'.isBlank()")
public class SmtpPasswordResetMailer implements PasswordResetMailer {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Override
    @Async
    public void sendResetLink(String toEmail, String fullName, String resetLink, long ttlMinutes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject("Reset your FinTrack password");
            helper.setText(buildBody(fullName, resetLink, ttlMinutes), true);
            mailSender.send(message);
            log.info("Password reset e-mail sent");
        } catch (Exception e) {
            // Swallowed on purpose: surfacing delivery failures would leak whether the account exists.
            log.error("Failed to send password reset e-mail", e);
        }
    }

    private String buildBody(String fullName, String resetLink, long ttlMinutes) {
        String greeting = (fullName == null || fullName.isBlank())
                ? "Hello,"
                : "Hello " + HtmlUtils.htmlEscape(fullName) + ",";
        String link = HtmlUtils.htmlEscape(resetLink);
        return """
                <p>%s</p>
                <p>We received a request to reset your FinTrack password. Click the button below to choose a new one.
                This link expires in %d minutes and can only be used once.</p>
                <p><a href="%s" style="display:inline-block;padding:10px 18px;background:#2563eb;color:#fff;border-radius:6px;text-decoration:none">Reset password</a></p>
                <p>If the button does not work, copy this URL into your browser:<br>%s</p>
                <p>If you did not request this, you can safely ignore this e-mail — your password will not change.</p>
                """.formatted(greeting, ttlMinutes, link, link);
    }
}
