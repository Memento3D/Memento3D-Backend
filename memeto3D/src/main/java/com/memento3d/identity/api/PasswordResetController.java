package com.memento3d.identity.api;

import com.memento3d.identity.api.request.ForgotPasswordRequest;
import com.memento3d.identity.api.request.ResetPasswordRequest;
import com.memento3d.identity.application.service.PasswordResetService;
import com.memento3d.shared.api.response.ErrorResponse;
import com.memento3d.shared.security.ClientIpResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Password Recovery", description = "Forgot password and reset password (public, rate limited)")
public class PasswordResetController {

    static final String NEUTRAL_MESSAGE =
            "Nếu email tồn tại và đủ điều kiện, chúng tôi sẽ gửi hướng dẫn đặt lại mật khẩu.";

    private final PasswordResetService passwordResetService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset email",
            description = "Always returns the same neutral message so account existence is never revealed. "
                    + "Rate limited per IP (10/h) and per email (3/h).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Neutral confirmation"),
            @ApiResponse(responseCode = "400", description = "Malformed email",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        passwordResetService.requestPasswordReset(request.getEmail(), clientIpResolver.resolve(servletRequest));
        return ResponseEntity.ok(Map.of("message", NEUTRAL_MESSAGE));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password with the emailed one-time token",
            description = "Token is single-use and expires after 15 minutes. Revokes all sessions. Does NOT log the user in.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password updated"),
            @ApiResponse(responseCode = "400", description = "INVALID_RESET_TOKEN or WEAK_PASSWORD",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword(), clientIpResolver.resolve(servletRequest));
        return ResponseEntity.ok(Map.of("message", "Mật khẩu đã được đặt lại thành công. Vui lòng đăng nhập bằng mật khẩu mới."));
    }
}
