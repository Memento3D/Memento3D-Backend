package com.memento3d.orders.api;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.orders.api.response.OrderListResponse;
import com.memento3d.orders.api.response.OrderResponse;
import com.memento3d.orders.application.service.OrderService;
import com.memento3d.orders.domain.model.OrderStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/manager/orders", "/api/manager/orders"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Manager Orders", description = "Order operations and status state transitions for Managers and Admins")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
public class ManagerOrderController {

    private final OrderService orderService;

    @GetMapping
    @Operation(summary = "List orders for manager", description = "Filter by status, search keyword, with pagination")
    public ResponseEntity<OrderListResponse> listOrders(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(orderService.getManagerOrders(query, status, page, limit));
    }

    @GetMapping("/counts")
    @Operation(summary = "Get order counts by status for manager")
    public ResponseEntity<Map<String, Long>> getOrderStatusCounts(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(orderService.getOrderStatusCounts(userDetails.getId(), userDetails.getRole()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order detail for manager")
    public ResponseEntity<OrderResponse> getOrderDetail(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Role role = userDetails.getRole();
        return ResponseEntity.ok(orderService.getOrderDetail(id, userDetails.getId(), role));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Transition order status", description = "Validates lifecycle transitions (e.g. CONFIRMED -> PROCESSING -> PRINTING -> READY -> COMPLETED)")
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        String statusStr = body.get("status");
        if (statusStr == null || statusStr.isBlank()) {
            throw new IllegalArgumentException("Status is required");
        }
        OrderStatus nextStatus = OrderStatus.valueOf(statusStr.toUpperCase());
        Role role = userDetails.getRole();
        OrderResponse response = orderService.updateOrderStatus(id, nextStatus, userDetails.getId(), role);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/feasibility-review")
    @Operation(summary = "Workshop inspection: approve feasibility, request revision, or reject custom 3D model")
    public ResponseEntity<OrderResponse> reviewFeasibility(
            @PathVariable UUID id,
            @jakarta.validation.Valid @RequestBody com.memento3d.orders.api.request.OrderFeasibilityReviewRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Role role = userDetails.getRole();
        return ResponseEntity.ok(orderService.reviewFeasibility(id, request, userDetails.getId(), role));
    }
}
