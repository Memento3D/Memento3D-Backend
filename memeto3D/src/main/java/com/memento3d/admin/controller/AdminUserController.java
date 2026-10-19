package com.memento3d.admin.controller;

import com.memento3d.admin.dto.AdminCreateUserRequest;
import com.memento3d.admin.dto.AdminUpdateUserRequest;
import com.memento3d.admin.dto.AdminUserDetailResponse;
import com.memento3d.admin.service.AdminUserService;
import com.memento3d.identity.api.response.UserResponse;
import com.memento3d.identity.domain.model.AuthProvider;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.UserStatus;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.shared.exception.BadRequestException;
import com.memento3d.warehouse.api.request.AssignMemberRequest;
import com.memento3d.warehouse.application.service.WarehouseService;
import com.memento3d.warehouse.domain.model.WarehouseRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Users", description = "User administration, search, filter, creation, status toggle, lock/unlock, and role management")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final WarehouseService warehouseService;

    @GetMapping("")
    @Operation(summary = "List users with search, role, status, provider filters and pagination", description = "Server-side search across fullName, email, and phone with pagination")
    public ResponseEntity<Page<UserResponse>> listUsers(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "role", required = false) Role role,
            @RequestParam(name = "status", required = false) UserStatus status,
            @RequestParam(name = "provider", required = false) AuthProvider provider,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sort", defaultValue = "createdAt,desc") String sort
    ) {
        String[] sortParts = sort.split(",");
        String sortField = sortParts[0].trim();
        Sort.Direction direction = (sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1].trim()))
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        PageRequest pageRequest = PageRequest.of(Math.max(0, page), size, Sort.by(direction, sortField));
        return ResponseEntity.ok(adminUserService.listUsers(search, role, status, provider, pageRequest));
    }

    @PostMapping("")
    @Operation(summary = "Create user account by administrator", description = "Creates a new user with BCrypt password encoding, verified status, and audit log")
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody AdminCreateUserRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        UserResponse response = adminUserService.createUser(request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get detailed user profile and aggregate usage statistics")
    public ResponseEntity<AdminUserDetailResponse> getUserDetail(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(adminUserService.getUserDetail(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update user profile, phone, role, or status")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AdminUpdateUserRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(adminUserService.updateUser(id, request, actorId));
    }

    @PatchMapping("/{id}/lock")
    @Operation(summary = "Lock user account (prevents login)")
    public ResponseEntity<UserResponse> lockUser(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(adminUserService.lockUser(id, actorId));
    }

    @PatchMapping("/{id}/unlock")
    @Operation(summary = "Unlock user account")
    public ResponseEntity<UserResponse> unlockUser(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(adminUserService.unlockUser(id, actorId));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate user account (status = ACTIVE)")
    public ResponseEntity<UserResponse> activateUser(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(adminUserService.activateUser(id, actorId));
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate user account (status = INACTIVE)")
    public ResponseEntity<UserResponse> deactivateUser(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(adminUserService.deactivateUser(id, actorId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user account (Soft-deletes if user has linked orders/jobs)")
    public ResponseEntity<Void> deleteUser(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        adminUserService.deleteUser(id, actorId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Update user role with audit logging")
    public ResponseEntity<UserResponse> updateUserRole(
            @PathVariable("id") UUID id,
            @RequestParam(name = "role", required = false) String roleParam,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        String roleStr = (roleParam != null && !roleParam.isBlank()) ? roleParam : (body != null ? body.get("role") : null);
        if (roleStr == null || roleStr.isBlank()) {
            throw new BadRequestException("Role không được để trống");
        }
        Role newRole = Role.valueOf(roleStr.trim().toUpperCase());
        return ResponseEntity.ok(adminUserService.updateUserRole(id, newRole, actorId));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Toggle user status (supports active boolean or status string)")
    public ResponseEntity<UserResponse> updateUserStatus(
            @PathVariable("id") UUID id,
            @RequestParam(name = "active", required = false) Boolean active,
            @RequestBody(required = false) Map<String, Object> body,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        Boolean isAct = active;
        if (isAct == null && body != null && body.containsKey("active")) {
            isAct = Boolean.TRUE.equals(body.get("active"));
        }
        if (isAct != null) {
            return isAct ? ResponseEntity.ok(adminUserService.activateUser(id, actorId))
                         : ResponseEntity.ok(adminUserService.deactivateUser(id, actorId));
        }
        String statusStr = body != null ? (String) body.get("status") : null;
        if (statusStr != null) {
            UserStatus targetStatus = UserStatus.valueOf(statusStr.toUpperCase());
            AdminUpdateUserRequest req = AdminUpdateUserRequest.builder().status(targetStatus).build();
            return ResponseEntity.ok(adminUserService.updateUser(id, req, actorId));
        }
        throw new BadRequestException("Cần cung cấp tham số active hoặc status");
    }

    @PutMapping("/{id}/warehouse-assignments")
    @Operation(summary = "Assign a user to multiple warehouses with specific scoped roles")
    public ResponseEntity<Void> assignUserWarehouses(
            @PathVariable("id") UUID id,
            @RequestBody List<WarehouseAssignmentRequest> assignments,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        for (WarehouseAssignmentRequest req : assignments) {
            AssignMemberRequest memberReq = AssignMemberRequest.builder()
                    .userId(id)
                    .role(req.getRole())
                    .build();
            warehouseService.assignMember(req.getWarehouseId(), memberReq, actorId, "ROLE_ADMIN");
        }
        return ResponseEntity.noContent().build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WarehouseAssignmentRequest {
        private UUID warehouseId;
        private WarehouseRole role;
    }
}
