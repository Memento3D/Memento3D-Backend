package com.memento3d.identity.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

/**
 * PATCH semantics: a {@code null} field is left untouched. Role, status, email, provider and id are
 * deliberately absent and unknown properties are REJECTED (400) so clients cannot attempt mass assignment.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(description = "Request payload to update current authenticated user profile (partial update)")
public class UpdateProfileRequest {

    @Size(min = 2, max = 100, message = "Họ tên phải từ 2 đến 100 ký tự")
    @Schema(description = "User display full name", example = "Nguyen Van A")
    private String fullName;

    @Size(max = 30, message = "Số điện thoại quá dài")
    @Schema(description = "Vietnamese mobile number. Empty string clears it.", example = "0912345678")
    private String phone;

    @Past(message = "Ngày sinh phải là một ngày trong quá khứ")
    @Schema(description = "Date of birth (ISO yyyy-MM-dd)", example = "1995-04-20")
    private LocalDate dateOfBirth;

    @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
    @Pattern(regexp = "^$|^https://\\S+$", message = "Avatar URL phải bắt đầu bằng https://")
    @Schema(description = "Avatar image URL (prefer POST /users/me/avatar)", example = "https://example.com/avatar.jpg")
    private String avatarUrl;
}
