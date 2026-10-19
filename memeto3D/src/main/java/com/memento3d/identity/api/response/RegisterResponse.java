package com.memento3d.identity.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Registration response")
public class RegisterResponse {

    @Schema(description = "Success message", example = "User registered successfully")
    private String message;

    @Schema(description = "Newly registered user details")
    private UserResponse user;

    @Schema(description = "Flag indicating email verification is required", example = "true")
    @Builder.Default
    private boolean verificationRequired = true;

    @Schema(description = "Verification token (included for development / automated testing convenience)")
    private String verificationToken;
}
