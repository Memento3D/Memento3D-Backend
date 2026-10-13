package com.memento3d.identity.api.response;

import com.memento3d.identity.domain.model.User;

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
}
