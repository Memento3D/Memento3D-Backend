package com.memento3d.admin.dto;

import com.memento3d.identity.api.response.UserResponse;
import com.memento3d.identity.domain.model.AuthProvider;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import com.memento3d.identity.domain.model.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed user profile with aggregate activity statistics")
public class AdminUserDetailResponse {

    private UUID id;
    private String email;
    private String fullName;
    private String phone;
    private Role role;
    private UserStatus status;
    private AuthProvider provider;
    private String avatarUrl;
    private Boolean emailVerified;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastLoginAt;

    @Schema(description = "Aggregated usage statistics")
    private UserStatistics statistics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserStatistics {
        private long totalOrders;
        private long completedOrders;
        private long cancelledOrders;
        private BigDecimal totalSpending;
        private long reconstructionJobs;
        private long printJobs;
    }

    public static AdminUserDetailResponse fromEntity(User user, UserStatistics stats) {
        if (user == null) return null;
        return AdminUserDetailResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole())
                .status(user.getStatus())
                .provider(user.getProvider())
                .avatarUrl(user.getAvatarUrl())
                .emailVerified(Boolean.TRUE.equals(user.getEmailVerified()))
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .statistics(stats)
                .build();
    }
}
