package com.memento3d.identity.api.response;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "User details response")
public class UserResponse {

    @Schema(description = "Unique user identifier", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private UUID id;

    @Schema(description = "User email address", example = "john.doe@example.com")
    private String email;

    @Schema(description = "User full name", example = "John Doe")
    private String fullName;

    @Schema(description = "Assigned user role", example = "USER")
    private Role role;

    @Schema(description = "Authentication provider", example = "GOOGLE")
    private com.memento3d.identity.domain.model.AuthProvider provider;

    @Schema(description = "User avatar image URL", example = "https://lh3.googleusercontent.com/a/...")
    private String avatarUrl;

    @Schema(description = "Whether the user's email is verified", example = "true")
    private Boolean emailVerified;

    @Schema(description = "User account status", example = "ACTIVE")
    private com.memento3d.identity.domain.model.UserStatus status;

    @Schema(description = "User phone number", example = "0912345678")
    private String phone;

    @Schema(description = "Last login timestamp")
    private java.time.Instant lastLoginAt;

    @Schema(description = "Account creation timestamp")
    private java.time.Instant createdAt;

    @Schema(description = "Last profile update timestamp")
    private java.time.Instant updatedAt;

    @Schema(description = "Date of birth (optional)", example = "1995-04-20")
    private java.time.LocalDate dateOfBirth;

    @Schema(description = "True when the account has a local password (false for Google-only accounts)", example = "true")
    private Boolean hasPassword;

    public static UserResponse fromEntity(User user) {
        if (user == null) {
            return null;
        }
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .provider(user.getProvider())
                .avatarUrl(user.getAvatarUrl())
                .emailVerified(Boolean.TRUE.equals(user.getEmailVerified()))
                .status(user.getStatus())
                .phone(user.getPhone())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .dateOfBirth(user.getDateOfBirth())
                .hasPassword(user.getPassword() != null && !user.getPassword().isBlank())
                .build();
    }
}
