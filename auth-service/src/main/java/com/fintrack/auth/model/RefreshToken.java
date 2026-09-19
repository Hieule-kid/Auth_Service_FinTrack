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
 * JPA entity storing issued refresh tokens.
 *
 * <p>Table: {@code refresh_tokens}
 *
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(
    name = "refresh_tokens",
    indexes = {
        @Index(name = "idx_refresh_tokens_token", columnList = "token", unique = true),
        @Index(name = "idx_refresh_tokens_user_id", columnList = "user_id")
    }
)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false, length = 36)
    private String id;

    /** The opaque token string sent to and stored by the client. */
    @NotBlank(message = "Token cannot be blank")
    @Column(name = "token", unique = true, nullable = false, length = 36)
    private String token;

    @NotBlank(message = "User ID cannot be blank")
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    /** Absolute expiry timestamp (UTC). */
    @NotNull(message = "Expiry date cannot be null")
    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;
}
