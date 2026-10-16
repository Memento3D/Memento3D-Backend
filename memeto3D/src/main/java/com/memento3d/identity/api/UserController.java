package com.memento3d.identity.api;

import com.memento3d.identity.api.request.AdminCreateUserRequest;
import com.memento3d.identity.api.request.AdminUpdateUserRequest;
import com.memento3d.identity.api.request.UpdateProfileRequest;
import com.memento3d.identity.api.response.UserListResponse;
import com.memento3d.identity.api.response.UserResponse;
import com.memento3d.identity.application.service.AuthService;
import com.memento3d.identity.application.service.UserService;
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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@Slf4j
@Validated
@Tag(name = "Users", description = "User profile and administration endpoints")
@SecurityRequirement(name = "BearerAuth")
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    public UserController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/users/me (Self Profile)
    // -------------------------------------------------------------------------
    @GetMapping({"/api/v1/users/me", "/api/me"})
    @Operation(
            summary = "Get current authenticated user profile",
            description = "Retrieves profile details of the currently authenticated user based on JWT in SecurityContextHolder"
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

    // -------------------------------------------------------------------------
    // PATCH /api/v1/users/me (Update Self Profile)
    // -------------------------------------------------------------------------
    @PatchMapping({"/api/v1/users/me", "/api/me"})
    @Operation(
            summary = "Update current user profile",
            description = "Allows the authenticated user to update their own display full name and avatar URL. Protected against mass assignment of sensitive fields."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User profile successfully updated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized: missing, invalid, or expired JWT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserResponse> updateCurrentUser(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        UUID currentUserId = getAuthenticatedUserId(userDetails);
        log.info("Received request to update profile for user ID: {}", currentUserId);
        UserResponse response = userService.updateMyProfile(currentUserId, request);
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/users (Admin / Operator List Users)
    // -------------------------------------------------------------------------
    @GetMapping("/api/v1/users")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_OPERATOR')")
    @Operation(
            summary = "List users with pagination and search (Admin/Operator)",
            description = "Retrieves a paginated list of users with optional keyword search across email and full name."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Users retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserListResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden: requires ROLE_ADMIN or ROLE_OPERATOR",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserListResponse> getUsers(
            @RequestParam(name = "search", required = false)
            @Parameter(description = "Keyword search for email or full name") String search,

            @RequestParam(name = "page", defaultValue = "1")
            @Min(value = 1, message = "Page must be at least 1") Integer page,

            @RequestParam(name = "limit", defaultValue = "20")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 100, message = "Limit cannot exceed 100") Integer limit
    ) {
        log.info("Admin list users request: search='{}', page={}, limit={}", search, page, limit);
        UserListResponse response = userService.getUsers(search, page, limit);
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/users (Admin Create User)
    // -------------------------------------------------------------------------
    @PostMapping("/api/v1/users")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(
            summary = "Admin create user account",
            description = "Allows an administrator to manually provision a user account with a designated role and storage quota."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User successfully created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden: requires ROLE_ADMIN",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email already registered",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody AdminCreateUserRequest request
    ) {
        log.info("Admin create user request for email: {}", request.getEmail());
        UserResponse response = userService.adminCreateUser(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + response.getId())).body(response);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/users/{userId}
    // -------------------------------------------------------------------------
    @GetMapping({"/api/v1/users/{userId}", "/api/v1/users/{userId:[0-9a-fA-F\\-]{36}}"})
    @Operation(
            summary = "Get user profile by ID",
            description = "Retrieves user profile details by ID using Redis cache first (key pattern user:{userId}), falling back to PostgreSQL."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User profile retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized: missing, invalid, or expired JWT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable("userId") UUID userId
    ) {
        log.info("Received request to get user by ID: {}", userId);
        UserResponse response = userService.getUserById(userId);
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // PATCH /api/v1/users/{userId} (Admin Update User)
    // -------------------------------------------------------------------------
    @PatchMapping("/api/v1/users/{userId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(
            summary = "Admin update user details",
            description = "Allows an administrator to update role, storage quota, full name, or avatar of a user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User successfully updated",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden: requires ROLE_ADMIN",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody AdminUpdateUserRequest request
    ) {
        log.info("Admin update request for user ID: {}", userId);
        UserResponse response = userService.adminUpdateUser(userId, request);
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // DELETE /api/v1/users/{userId} (Admin Delete / Deactivate User)
    // -------------------------------------------------------------------------
    @DeleteMapping("/api/v1/users/{userId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(
            summary = "Admin delete/deactivate user",
            description = "Removes or deactivates a user account and evicts cached credentials and profile."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User successfully deleted"),
            @ApiResponse(responseCode = "403", description = "Forbidden: requires ROLE_ADMIN",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteUser(
            @PathVariable("userId") UUID userId
    ) {
        log.info("Admin delete request for user ID: {}", userId);
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    private UUID getAuthenticatedUserId(CustomUserDetails userDetails) {
        if (userDetails != null && userDetails.getId() != null) {
            return userDetails.getId();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new InvalidTokenException("No authenticated user in security context");
        }
        if (authentication.getPrincipal() instanceof CustomUserDetails cud) {
            return cud.getId();
        }
        throw new InvalidTokenException("Invalid user details in security context");
    }
}
