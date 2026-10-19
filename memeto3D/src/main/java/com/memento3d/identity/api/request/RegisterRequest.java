package com.memento3d.identity.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request body for user registration")
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Schema(description = "User email address", example = "john.doe@example.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters long")
    @Schema(description = "User password (minimum 6 characters)", example = "SecurePassword123!")
    private String password;

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name must not exceed 100 characters")
    @Schema(description = "User full name", example = "John Doe")
    private String fullName;

    @Schema(description = "Agreement to Terms of Service and Privacy Policy", example = "true")
    private Boolean agreeTerms;

    @Schema(description = "Accepted Terms of Service version", example = "1.0")
    private String termsVersion;

    @Schema(description = "Consent to receive promotional marketing emails", example = "false")
    @Builder.Default
    private Boolean marketingConsent = false;
}
