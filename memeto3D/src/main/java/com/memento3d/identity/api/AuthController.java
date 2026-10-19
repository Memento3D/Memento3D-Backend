package com.memento3d.identity.api;

import com.memento3d.identity.api.request.ConsentRequest;
import com.memento3d.identity.api.request.LoginRequest;
import com.memento3d.identity.api.request.RefreshTokenRequest;
import com.memento3d.identity.api.request.RegisterRequest;
import com.memento3d.identity.api.request.ResendVerificationRequest;
import com.memento3d.identity.api.request.VerifyEmailRequest;
import com.memento3d.identity.api.response.AuthResponse;
import com.memento3d.identity.api.response.LogoutResponse;
import com.memento3d.identity.api.response.RegisterResponse;
import com.memento3d.identity.api.response.UserResponse;
import com.memento3d.identity.api.response.VerifyEmailResponse;
import com.memento3d.shared.api.response.ErrorResponse;
import com.memento3d.identity.application.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/auth", "/api/auth"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Authentication endpoints: Register, Login, Refresh Token, Logout, Email Verification, Consents, and Profile")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register new Customer", description = "Creates a new customer account with hashed password and sends verification email")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User successfully registered",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RegisterResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error, missing terms agreement, or weak password",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email already registered in the system",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Received registration request for email: {}", request.getEmail());
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticates user credentials and returns JWT access token and persistent refresh token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authentication successful, returns tokens",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or missing fields",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid email or password",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Email not verified or account suspended",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Received login request for email: {}", request.getEmail());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh JWT token", description = "Uses a valid refresh token to issue a new JWT access token and rotated refresh token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token refresh successful",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing or invalid payload",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("Received refresh token request");
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(
            summary = "User logout",
            description = "Revokes refresh token and blacklists current JWT access token",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logout successful, token blacklisted and refresh token revoked",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = LogoutResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized: missing, invalid, or expired JWT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<LogoutResponse> logout(
            @Parameter(description = "Bearer authorization header", hidden = true)
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) RefreshTokenRequest request
    ) {
        log.info("Received logout request");
        String refreshTokenStr = request != null ? request.getRefreshToken() : null;
        LogoutResponse response = authService.logout(authHeader, refreshTokenStr);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    @Operation(
            summary = "Get current authenticated user profile",
            description = "Retrieves profile details of the currently authenticated user based on JWT in SecurityContextHolder",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current user profile retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized: missing, invalid, or expired JWT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserResponse> getCurrentUser() {
        log.info("Received request to get current logged-in user profile");
        UserResponse response = authService.getCurrentUser();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/email/verify")
    @Operation(summary = "Verify customer email address", description = "Validates the one-time email verification token and activates customer account")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email successfully verified",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = VerifyEmailResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid, expired, or reused token",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<VerifyEmailResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        log.info("Received request to verify email token");
        VerifyEmailResponse response = authService.verifyEmail(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/email/resend-verification")
    @Operation(summary = "Resend verification email", description = "Resends an email verification link with rate limiting to prevent spam")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verification email dispatched if account exists and is unverified"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded (less than 60 seconds since last attempt)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, String>> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        log.info("Received request to resend verification email for: {}", request.getEmail());
        authService.resendVerificationEmail(request);
        return ResponseEntity.ok(Map.of(
                "message", "Nếu địa chỉ email tồn tại trong hệ thống và chưa được kích hoạt, liên kết xác thực mới đã được gửi."
        ));
    }

    @PostMapping("/consents")
    @Operation(
            summary = "Complete required agreements (Google Onboarding / Consent update)",
            description = "Accepts Terms of Service and optional marketing consent for authenticated onboarding session",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agreements recorded and full session tokens issued",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Terms of Service agreement is mandatory",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized or invalid onboarding token",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AuthResponse> completeConsents(
            @Valid @RequestBody ConsentRequest request,
            HttpServletRequest servletRequest
    ) {
        String clientIp = servletRequest.getRemoteAddr();
        String userAgent = servletRequest.getHeader("User-Agent");
        log.info("Received complete consents request from IP: {}", clientIp);
        AuthResponse response = authService.completeConsents(request, clientIp, userAgent);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/oauth2/google")
    @Operation(
            summary = "Login with Google (OAuth2 / OIDC)",
            description = "Redirects browser to Google OAuth2 authorization screen"
    )
    public void loginWithGoogle(HttpServletResponse response) throws IOException {
        response.sendRedirect("/oauth2/authorization/google");
    }

    @GetMapping("/oauth2/google/url")
    @Operation(
            summary = "Get Google Login URL",
            description = "Returns JSON containing Google OAuth2 authorization initiation URL for frontend consumption"
    )
    public ResponseEntity<Map<String, String>> getGoogleLoginUrl() {
        return ResponseEntity.ok(Map.of(
                "url", "http://localhost:8080/oauth2/authorization/google",
                "message", "Mở URL này trên tab trình duyệt để bắt đầu đăng nhập với Google"
        ));
    }
}
