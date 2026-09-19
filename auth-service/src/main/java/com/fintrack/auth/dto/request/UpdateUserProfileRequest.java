package com.fintrack.auth.dto.request;

import com.fintrack.auth.model.enums.Currency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Request payload for the {@code PUT /api/v1/users/profile} endpoint.
 *
 */
@Getter
@Setter
public class UpdateUserProfileRequest {

    @Schema(description = "Full display name of the user", example = "Alice Smith")
    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    private String fullName;

    @Schema(description = "Email address, also used as an alternative login identifier", example = "alice@example.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @Schema(description = "Preferred currency for the user", example = "USD")
    @NotNull(message = "Currency is required")
    private Currency currency;
}
