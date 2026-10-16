package com.memento3d.identity.api.request;

import com.memento3d.identity.domain.model.Role;
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
@Schema(description = "Admin request payload to provision a new user account")
public class AdminCreateUserRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    @Schema(description = "Unique email address for user account", example = "newuser@memento3d.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    @Schema(description = "Raw initial password", example = "StrongP@ssw0rd")
    private String password;

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    @Schema(description = "Full name of the user", example = "Administrator Agent")
    private String fullName;

    @Schema(description = "Assigned user role (ROLE_USER, ROLE_ADMIN, ROLE_OPERATOR)", example = "ROLE_USER")
    @Builder.Default
    private Role role = Role.ROLE_USER;

    @Schema(description = "Storage quota in bytes (default 10 GB = 10737418240)", example = "10737418240")
    private Long storageQuotaBytes;
}
