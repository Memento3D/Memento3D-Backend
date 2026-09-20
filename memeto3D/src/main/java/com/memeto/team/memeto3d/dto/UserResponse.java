package com.memeto.team.memeto3d.dto;

import com.memeto.team.memeto3d.entity.Role;
import com.memeto.team.memeto3d.entity.User;
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

    public static UserResponse fromEntity(User user) {
        if (user == null) {
            return null;
        }
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .build();
    }
}
