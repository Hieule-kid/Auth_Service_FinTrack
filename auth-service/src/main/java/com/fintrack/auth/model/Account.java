package com.fintrack.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity representing OAuth2 linked accounts.
 *
 * <p>Table: {@code accounts}
 * <p>Unique constraint: {@code user_id + provider + provider_account_id}
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
    name = "accounts",
    indexes = {
        @Index(name = "idx_accounts_user_id", columnList = "user_id"),
        @Index(name = "idx_accounts_provider", columnList = "provider"),
        @Index(name = "idx_accounts_provider_account_id", columnList = "provider_account_id"),
        @Index(
            name = "idx_accounts_user_provider_unique",
            columnList = "user_id,provider,provider_account_id",
            unique = true
        )
    }
)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false, length = 36)
    private String id;

    @NotNull(message = "User cannot be null")
    @ManyToOne(optional = false)
    @JoinColumn(
        name = "user_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_accounts_user_id")
    )
    private User user;

    @NotBlank(message = "Provider cannot be blank")
    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @NotBlank(message = "Provider account ID cannot be blank")
    @Column(name = "provider_account_id", nullable = false, length = 255)
    private String providerAccountId;

    @NotNull(message = "Access token cannot be null")
    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;
}

