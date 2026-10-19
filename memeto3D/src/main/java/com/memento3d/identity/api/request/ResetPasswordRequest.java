package com.memento3d.identity.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Set a new password using the one-time token received by email")
public class ResetPasswordRequest {

    @NotBlank(message = "Liên kết đặt lại mật khẩu không hợp lệ")
    @Size(max = 200, message = "Liên kết đặt lại mật khẩu không hợp lệ")
    @Schema(description = "One-time reset token from the email link")
    private String token;

    @NotBlank(message = "Vui lòng nhập mật khẩu mới")
    @Size(min = 12, max = 128, message = "Mật khẩu phải từ 12 đến 128 ký tự")
    @Schema(example = "ExampleStrongPassword2026!")
    private String newPassword;
}
