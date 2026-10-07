package com.memeto.team.memeto3d.controller;

import com.memeto.team.memeto3d.dto.ErrorResponse;
import com.memeto.team.memeto3d.dto.UserResponse;
import com.memeto.team.memeto3d.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Users", description = "User management and profile lookup endpoints")
public class UserController {

    private final UserService userService;
    private final com.memeto.team.memeto3d.service.AuthService authService;

    @GetMapping({"/users/me", "/api/v1/users/me", "/api/users/me"})
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

    @GetMapping({"/users/{id}", "/api/v1/users/{id}", "/api/users/{id}"})
    @Operation(
            summary = "Get user profile by ID",
            description = "Retrieves user profile details by ID using Redis cache first (key pattern user:{userId}), falling back to PostgreSQL source of truth on cache miss.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User profile retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized: missing, invalid, or expired JWT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserResponse> getUserById(@PathVariable("id") UUID id) {
        log.info("Received request to get user by ID: {}", id);
        UserResponse response = userService.getUserById(id);
        return ResponseEntity.ok(response);
    }
}
