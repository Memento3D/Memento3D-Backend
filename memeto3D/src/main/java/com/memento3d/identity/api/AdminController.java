package com.memento3d.identity.api;

import com.memento3d.identity.api.response.UserResponse;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.domain.model.User;
import com.memento3d.identity.infrastructure.persistence.repository.UserRepository;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.inventory.api.response.AdminDashboardResponse;
import com.memento3d.inventory.api.response.InventoryTransactionResponse;
import com.memento3d.inventory.application.service.InventoryService;
import com.memento3d.inventory.domain.model.EnterpriseStockIssueStatus;
import com.memento3d.inventory.domain.model.GoodsReceiptStatus;
import com.memento3d.inventory.domain.model.StockAdjustmentStatus;
import com.memento3d.inventory.domain.model.WarehouseTransferStatus;
import com.memento3d.inventory.infrastructure.persistence.repository.*;
import com.memento3d.material.domain.model.MaterialStatus;
import com.memento3d.material.infrastructure.persistence.repository.MaterialRepository;
import com.memento3d.shared.api.response.AuditLogResponse;
import com.memento3d.shared.application.service.AuditLogService;
import com.memento3d.shared.domain.model.AuditLog;
import com.memento3d.shared.exception.ResourceNotFoundException;
import com.memento3d.warehouse.api.request.AssignMemberRequest;
import com.memento3d.warehouse.application.service.WarehouseService;
import com.memento3d.warehouse.domain.model.WarehouseRole;
import com.memento3d.warehouse.domain.model.WarehouseStatus;
import com.memento3d.warehouse.infrastructure.persistence.repository.WarehouseRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Operations", description = "Global administration, user roles, warehouse assignments, and audit logs")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final MaterialRepository materialRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final EnterpriseStockIssueRepository stockIssueRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final WarehouseTransferRepository warehouseTransferRepository;
    private final InventoryService inventoryService;
    private final WarehouseService warehouseService;
    private final AuditLogService auditLogService;

    @GetMapping("/dashboard")
    @Operation(summary = "Get global admin dashboard statistics")
    public ResponseEntity<AdminDashboardResponse> getAdminDashboard() {
        long totalUsers = userRepository.count();
        long totalManagers = userRepository.countByRole(Role.ROLE_MANAGER);
        long totalWarehouseStaff = userRepository.countByRole(Role.ROLE_WAREHOUSE);
        long totalWarehouses = warehouseRepository.count();
        long totalMaterials = materialRepository.count();

        long lowStockCount = inventoryBalanceRepository.countLowStock(null);
        long outOfStockCount = inventoryBalanceRepository.countOutOfStock(null);
        BigDecimal totalInventoryValue = inventoryBalanceRepository.calculateTotalInventoryValue(null);

        long pendingApprovals = goodsReceiptRepository.countByStatus(GoodsReceiptStatus.SUBMITTED) +
                stockIssueRepository.countByStatus(EnterpriseStockIssueStatus.SUBMITTED) +
                stockAdjustmentRepository.countByStatus(StockAdjustmentStatus.SUBMITTED) +
                warehouseTransferRepository.countByStatus(WarehouseTransferStatus.SUBMITTED);

        List<InventoryTransactionResponse> recentMovements = inventoryService
                .listTransactions(null, null, null, null, PageRequest.of(0, 10))
                .getContent();

        AdminDashboardResponse response = AdminDashboardResponse.builder()
                .totalUsers(totalUsers)
                .totalManagers(totalManagers)
                .totalWarehouseStaff(totalWarehouseStaff)
                .totalWarehouses(totalWarehouses)
                .totalMaterials(totalMaterials)
                .lowStockCount(lowStockCount)
                .outOfStockCount(outOfStockCount)
                .pendingApprovals(pendingApprovals)
                .totalInventoryValue(totalInventoryValue != null ? totalInventoryValue : BigDecimal.ZERO)
                .recentMovements(recentMovements)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/users")
    @Operation(summary = "List users with role and search filters for Admin")
    public ResponseEntity<Page<UserResponse>> listUsers(
            @RequestParam(name = "role", required = false) Role role,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(userRepository.findAllWithFilters(role, cleanSearch, pageRequest).map(UserResponse::fromEntity));
    }

    @PatchMapping("/users/{id}/role")
    @Operation(summary = "Change a user's system role (ROLE_ADMIN, ROLE_MANAGER, ROLE_WAREHOUSE, ROLE_OPERATOR)")
    public ResponseEntity<UserResponse> updateUserRole(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        String roleStr = body.get("role");
        Role newRole = Role.valueOf(roleStr.toUpperCase());
        Role oldRole = user.getRole();
        user.setRole(newRole);
        User saved = userRepository.save(user);

        log.info("Admin updated role for user {} from {} to {}", user.getEmail(), oldRole, newRole);
        UUID actorId = currentUser != null ? currentUser.getId() : null;
        auditLogService.log(actorId, "UPDATE_USER_ROLE", "User", user.getId().toString(), oldRole.name(), newRole.name());

        return ResponseEntity.ok(UserResponse.fromEntity(saved));
    }

    @PutMapping("/users/{id}/warehouse-assignments")
    @Operation(summary = "Assign a user to multiple warehouses with specific scoped roles")
    public ResponseEntity<Void> assignUserWarehouses(
            @PathVariable("id") UUID id,
            @RequestBody List<WarehouseAssignmentRequest> assignments,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        UUID actorId = currentUser != null ? currentUser.getId() : null;

        for (WarehouseAssignmentRequest req : assignments) {
            AssignMemberRequest memberReq = AssignMemberRequest.builder()
                    .userId(user.getId())
                    .role(req.getRole())
                    .build();
            warehouseService.assignMember(req.getWarehouseId(), memberReq, actorId, "ROLE_ADMIN");
        }

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Query system audit logs")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogs(
            @RequestParam(name = "actorId", required = false) UUID actorId,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "entityType", required = false) String entityType,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(auditLogService.getAuditLogs(actorId, action, entityType, pageRequest).map(this::mapAuditLog));
    }

    private AuditLogResponse mapAuditLog(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .actorId(log.getActorId())
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .beforeData(log.getBeforeData())
                .afterData(log.getAfterData())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .createdAt(log.getCreatedAt())
                .build();
    }

    @lombok.Data
    public static class WarehouseAssignmentRequest {
        private UUID warehouseId;
        private WarehouseRole role;
    }
}
