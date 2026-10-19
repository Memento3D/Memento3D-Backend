package com.memento3d.admin.dto;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Admin payload to update user account details")
public class AdminUpdateUserRequest {

    @Size(min = 2, max = 100, message = "Họ và tên phải từ 2 đến 100 ký tự")
    @Schema(description = "User full name", example = "Trần Quản Trị")
    private String fullName;

    @Pattern(regexp = "^$|^[0-9+() -]{8,20}$", message = "Số điện thoại không hợp lệ")
    @Schema(description = "User phone number", example = "0987654321")
    private String phone;

    @Schema(description = "Updated role")
    private Role role;

    @Schema(description = "Updated status")
    private UserStatus status;
}
