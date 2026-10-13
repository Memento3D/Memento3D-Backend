package com.memento3d.identity.api;

import com.memento3d.identity.api.request.LoginRequest;
import com.memento3d.identity.api.request.RefreshTokenRequest;
import com.memento3d.identity.api.request.RegisterRequest;
import com.memento3d.identity.api.response.AuthResponse;
import com.memento3d.identity.api.response.LogoutResponse;
import com.memento3d.identity.api.response.RegisterResponse;
import com.memento3d.identity.api.response.UserResponse;
import com.memento3d.identity.domain.model.RefreshToken;
import com.memento3d.identity.domain.model.User;
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Authentication endpoints: Register, Login, Refresh Token, Logout, and Current User profile")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register new user", description = "Creates a new user account with hashed password in PostgreSQL")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User successfully registered",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = RegisterResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or invalid request payload",
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

    @GetMapping("/oauth2/google")
    @Operation(
            summary = "Login with Google (OAuth2 / OIDC)",
            description = "Chuyển hướng đến màn hình xác thực tài khoản Google. <br/><br/>"
                    + "⚠️ <b>LƯU Ý:</b> Không dùng nút 'Execute' màu xanh của Swagger vì đây là API chuyển hướng trình duyệt (trình duyệt sẽ chặn CORS khi fetch ngầm sang Google). <br/>"
                    + "👉 Hãy click chuột phải mở link này trong tab mới: <a href=\"http://localhost:8080/oauth2/authorization/google\" target=\"_blank\"><b>BẤM VÀO ĐÂY ĐỂ ĐĂNG NHẬP BẰNG GOOGLE</b></a>"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Redirect to Google authorization screen")
    })
    public void loginWithGoogle(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        response.sendRedirect("/oauth2/authorization/google");
    }

    @GetMapping("/oauth2/google/url")
    @Operation(
            summary = "Get Google Login URL (Cho Frontend / Swagger test)",
            description = "Trả về JSON chứa URL đăng nhập Google để Frontend hoặc Swagger có thể lấy và mở trực tiếp."
    )
    public ResponseEntity<java.util.Map<String, String>> getGoogleLoginUrl() {
        return ResponseEntity.ok(java.util.Map.of(
                "url", "http://localhost:8080/oauth2/authorization/google",
                "message", "Mở URL này trên tab trình duyệt mới để đăng nhập Google"
        ));
    }
}
