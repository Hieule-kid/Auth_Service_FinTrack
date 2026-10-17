package com.fintrack.auth.config;

import com.fintrack.auth.repository.PasswordResetTokenRepository;
import com.fintrack.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Scheduled job to clean up expired refresh tokens from the database.
 *
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Scheduled(cron = "0 0 2 * * *")
    public void cleanExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        log.info("Running expired token cleanup at {}", now);

        refreshTokenRepository.deleteAllExpiredBefore(now);

        passwordResetTokenRepository.deleteAllExpiredBefore(Instant.now());

        log.info("Expired refresh and password reset tokens cleanup completed");
    }
}

