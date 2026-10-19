package com.memento3d.identity.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request body for completing terms and privacy consents")
public class ConsentRequest {

    @NotNull(message = "Agreement to Terms of Service is required")
    @AssertTrue(message = "You must agree to the Terms of Service to continue")
    @Schema(description = "Consent to Terms of Service", example = "true")
    private Boolean agreeTerms;

    @NotBlank(message = "Terms version is required")
    @Schema(description = "Terms of Service version string", example = "1.0")
    private String termsVersion;

    @Schema(description = "Consent to promotional marketing emails", example = "false")
    @Builder.Default
    private Boolean marketingConsent = false;
}
