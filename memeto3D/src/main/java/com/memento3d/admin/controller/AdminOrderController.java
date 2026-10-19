package com.memento3d.admin.controller;

import com.memento3d.orders.api.response.OrderResponse;
import com.memento3d.orders.domain.model.Order;
import com.memento3d.orders.domain.model.OrderStatus;
import com.memento3d.orders.infrastructure.persistence.repository.OrderRepository;
import com.memento3d.payment.domain.model.PaymentStatus;
import com.memento3d.shared.exception.ResourceNotFoundException;
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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Orders", description = "Global order monitoring, inspection, search, filter, and detail viewing")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderRepository orderRepository;

    @GetMapping("")
    @Operation(summary = "Search and filter all system orders with pagination", description = "Search by order code, recipient name, phone, filter by order status, payment status, and user ID")
    public ResponseEntity<Page<OrderResponse>> listOrders(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) OrderStatus status,
            @RequestParam(name = "paymentStatus", required = false) PaymentStatus paymentStatus,
            @RequestParam(name = "userId", required = false) UUID userId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sort", defaultValue = "createdAt,desc") String sort
    ) {
        String[] sortParts = sort.split(",");
        String sortField = sortParts[0].trim();
        Sort.Direction direction = (sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1].trim()))
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        PageRequest pageRequest = PageRequest.of(Math.max(0, page), size, Sort.by(direction, sortField));
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;

        Page<Order> orderPage = orderRepository.searchAdminOrders(cleanSearch, status, paymentStatus, userId, pageRequest);
        return ResponseEntity.ok(orderPage.map(OrderResponse::fromEntity));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get full order details with order items, product snapshots, and customer metadata")
    public ResponseEntity<OrderResponse> getOrderDetail(@PathVariable("id") UUID id) {
        Order order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với mã ID: " + id));
        return ResponseEntity.ok(OrderResponse.fromEntity(order));
    }
}
