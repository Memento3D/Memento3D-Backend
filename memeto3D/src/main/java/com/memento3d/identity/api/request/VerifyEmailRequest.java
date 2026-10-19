package com.memento3d.identity.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request body for verifying email address with token")
public class VerifyEmailRequest {

    @NotBlank(message = "Verification token is required")
    @Schema(description = "Verification token received via email", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String token;
}
