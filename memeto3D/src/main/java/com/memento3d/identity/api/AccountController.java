package com.memento3d.identity.api;

import com.memento3d.identity.api.request.ChangePasswordRequest;
import com.memento3d.identity.api.response.UserResponse;
import com.memento3d.identity.application.service.AccountService;
import com.memento3d.identity.domain.exception.InvalidTokenException;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.shared.api.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/**
 * Self-service account security. The user id is ALWAYS taken from the authenticated principal;
 * no endpoint accepts a user id from the client (prevents IDOR).
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Tag(name = "Account Security", description = "Change password and avatar of the authenticated user")
@SecurityRequirement(name = "BearerAuth")
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/change-password")
    @Operation(summary = "Change password",
            description = "Verifies the current password, applies the password policy, revokes all other sessions "
                    + "(the caller must sign in again) and emails a security notice.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password changed"),
            @ApiResponse(responseCode = "400", description = "INVALID_CURRENT_PASSWORD, PASSWORD_REUSE_NOT_ALLOWED, WEAK_PASSWORD or NO_LOCAL_PASSWORD",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "429", description = "Too many attempts")
    })
    public ResponseEntity<Map<String, Object>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        UUID userId = requireUserId(principal);
        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7).trim() : null;
        accountService.changePassword(userId, request, token);
        return ResponseEntity.ok(Map.of(
                "message", "Đổi mật khẩu thành công. Vui lòng đăng nhập lại.",
                "reauthenticationRequired", true
        ));
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload avatar",
            description = "multipart/form-data, field 'file'. JPG, PNG or WebP, max 5 MB. Signature is verified server side.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Avatar updated, returns the refreshed profile",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "INVALID_AVATAR",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserResponse> uploadAvatar(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ResponseEntity.ok(accountService.uploadAvatar(requireUserId(principal), file));
    }

    @DeleteMapping("/avatar")
    @Operation(summary = "Remove avatar", description = "Clears the avatar so the default placeholder is shown.")
    public ResponseEntity<UserResponse> removeAvatar(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(accountService.removeAvatar(requireUserId(principal)));
    }

    private UUID requireUserId(CustomUserDetails principal) {
        if (principal == null || principal.getId() == null) {
            throw new InvalidTokenException("No authenticated user in security context");
        }
        return principal.getId();
    }
}
