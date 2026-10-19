package com.memento3d.identity.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Response for email verification")
public class VerifyEmailResponse {

    @Schema(description = "Status message", example = "Email verified successfully")
    private String message;

    @Schema(description = "Verified email address", example = "john.doe@example.com")
    private String email;

    @Schema(description = "Verification status flag", example = "true")
    private boolean verified;
}
