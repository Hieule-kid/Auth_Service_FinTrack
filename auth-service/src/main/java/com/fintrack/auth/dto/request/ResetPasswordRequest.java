package com.fintrack.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Request payload for {@code POST /api/v1/auth/reset-password}.
 *
 * <p>Password rules intentionally mirror {@link RegisterRequest}.
 */
@Getter
@Setter
public class ResetPasswordRequest {

    @NotBlank(message = "Reset token is required")
    @Size(max = 200, message = "Invalid reset token")
    private String token;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    @Pattern(regexp = ".*\\d.*", message = "Password must contain at least one digit")
    private String newPassword;
}
