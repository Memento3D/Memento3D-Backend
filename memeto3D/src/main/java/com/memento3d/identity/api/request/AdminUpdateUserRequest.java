package com.memento3d.identity.api.request;

import com.memento3d.identity.domain.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Admin request payload to update an existing user account")
public class AdminUpdateUserRequest {

    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    @Schema(description = "Updated full name", example = "Jane Doe Updated")
    private String fullName;

    @Schema(description = "Updated role (ROLE_USER, ROLE_ADMIN, ROLE_OPERATOR)", example = "ROLE_OPERATOR")
    private Role role;

    @Schema(description = "Updated storage quota in bytes", example = "21474836480")
    private Long storageQuotaBytes;

    @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
    @Schema(description = "Updated avatar URL", example = "https://example.com/avatar.jpg")
    private String avatarUrl;
}
