package com.memento3d.orders.api;

import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.orders.api.request.CreateOrderRequest;
import com.memento3d.orders.api.request.UpdateOrderRequest;
import com.memento3d.orders.api.response.OrderListResponse;
import com.memento3d.orders.api.response.OrderResponse;
import com.memento3d.orders.application.service.OrderService;
import com.memento3d.orders.domain.model.OrderStatus;
import com.memento3d.payment.api.request.CreatePaymentRequest;
import com.memento3d.payment.api.response.PaymentResponse;
import com.memento3d.payment.application.service.PaymentService;
import com.memento3d.payment.domain.model.PaymentMethod;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/orders", "/api/orders"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Orders", description = "3D Print Order & Customer Checkout Management APIs")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create order", description = "Checkout current cart or submit bespoke scan order")
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        OrderResponse response = orderService.createOrder(request, userDetails.getId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getId())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my orders", description = "List orders belonging to the authenticated customer with status filter")
    public ResponseEntity<OrderListResponse> getMyOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        OrderListResponse response = orderService.getMyOrders(userDetails.getId(), status, page, limit);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List orders", description = "Retrieves orders. Regular users see their own orders; Admins and Operators see all orders.")
    public ResponseEntity<OrderListResponse> listOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Role role = userDetails.getRole();
        OrderListResponse response = orderService.getUserOrders(userDetails.getId(), role, page, limit);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get order details", description = "Retrieves order detail by ID. Enforces ownership check.")
    public ResponseEntity<OrderResponse> getOrderDetail(
            @Parameter(description = "Order UUID") @PathVariable UUID orderId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Role role = userDetails.getRole();
        OrderResponse response = orderService.getOrderDetail(orderId, userDetails.getId(), role);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{orderId}/payments")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Initiate order payment", description = "Initiate payment for an order with MOMO, ZALOPAY, or CASH")
    public ResponseEntity<PaymentResponse> initiateOrderPayment(
            @PathVariable UUID orderId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        String methodStr = (String) body.get("paymentMethod");
        if (methodStr == null || methodStr.isBlank()) {
            methodStr = "MOMO";
        }
        PaymentMethod paymentMethod = PaymentMethod.valueOf(methodStr.toUpperCase());
        String redirectUrl = (String) body.get("redirectUrl");
        String orderInfo = (String) body.get("orderInfo");

        CreatePaymentRequest paymentRequest = CreatePaymentRequest.builder()
                .orderId(orderId)
                .paymentMethod(paymentMethod)
                .redirectUrl(redirectUrl)
                .orderInfo(orderInfo != null ? orderInfo : "Payment for Order " + orderId)
                .build();

        PaymentResponse response = paymentService.createPayment(paymentRequest, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{orderId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update order", description = "Update recipient and shipping address. Admins / Operators can update order status.")
    public ResponseEntity<OrderResponse> updateOrder(
            @Parameter(description = "Order UUID") @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Role role = userDetails.getRole();
        OrderResponse response = orderService.updateOrder(orderId, request, userDetails.getId(), role);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{orderId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cancel order", description = "Cancels an order if in PENDING status or if requested by Admin/Operator")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> cancelOrder(
            @Parameter(description = "Order UUID") @PathVariable UUID orderId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Role role = userDetails.getRole();
        orderService.cancelOrder(orderId, userDetails.getId(), role);
        return ResponseEntity.noContent().build();
    }
}
