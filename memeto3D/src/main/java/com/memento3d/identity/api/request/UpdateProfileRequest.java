package com.memento3d.identity.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request payload to update current authenticated user profile")
public class UpdateProfileRequest {

    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    @Schema(description = "User display full name", example = "Jane Doe")
    private String fullName;

    @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
    @Schema(description = "Avatar image URL", example = "https://example.com/avatar.jpg")
    private String avatarUrl;
}
