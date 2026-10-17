package com.fintrack.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * JPA entity storing issued password-reset tokens.
 *
 * <p>Only the SHA-256 hash of the token is persisted; the raw value exists solely in the
 * e-mailed link. A database leak therefore cannot be replayed into account takeovers.
 *
 * <p>Table: {@code password_reset_tokens}
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(
    name = "password_reset_tokens",
    indexes = {
        @Index(name = "idx_password_reset_tokens_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_password_reset_tokens_user_id", columnList = "user_id")
    }
)
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false, length = 36)
    private String id;

    /** Hex-encoded SHA-256 of the raw token. */
    @NotBlank(message = "Token hash cannot be blank")
    @Column(name = "token_hash", unique = true, nullable = false, length = 64)
    private String tokenHash;

    @NotBlank(message = "User ID cannot be blank")
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    /** Absolute expiry timestamp (UTC). */
    @NotNull(message = "Expiry date cannot be null")
    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;

    /** Used to throttle repeated requests for the same account. */
    @NotNull(message = "Created timestamp cannot be null")
    // DB default lets `ddl-auto: update` add this NOT NULL column to a pre-existing table with rows.
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "timestamp(6) with time zone default now()")
    private Instant createdAt;
}
