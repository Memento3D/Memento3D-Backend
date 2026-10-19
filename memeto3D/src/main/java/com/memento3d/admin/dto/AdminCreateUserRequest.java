package com.memento3d.admin.dto;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@Schema(description = "Admin payload to create a new user account")
public class AdminCreateUserRequest {

    @NotBlank(message = "Họ và tên không được để trống")
    @Size(min = 2, max = 100, message = "Họ và tên phải từ 2 đến 100 ký tự")
    @Schema(description = "User full name", example = "Trần Quản Trị")
    private String fullName;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    @Schema(description = "User email", example = "operator.hn@memento3d.com")
    private String email;

    @Pattern(regexp = "^$|^[0-9+() -]{8,20}$", message = "Số điện thoại không hợp lệ")
    @Schema(description = "User phone number", example = "0987654321")
    private String phone;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6, max = 100, message = "Mật khẩu tối thiểu 6 ký tự")
    @Schema(description = "Initial password", example = "AdminSecure@2026")
    private String password;

    @NotNull(message = "Vai trò không được để trống")
    @Schema(description = "Role to assign", example = "ROLE_OPERATOR")
    private Role role;

    @Builder.Default
    @Schema(description = "Initial user status", example = "ACTIVE")
    private UserStatus status = UserStatus.ACTIVE;
}
