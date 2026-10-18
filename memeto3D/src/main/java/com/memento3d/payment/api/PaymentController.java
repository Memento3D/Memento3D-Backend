package com.memento3d.payment.api;

import com.memento3d.identity.domain.exception.InvalidTokenException;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.payment.api.request.CreatePaymentRequest;
import com.memento3d.payment.api.response.PaymentResponse;
import com.memento3d.payment.api.response.PaymentStatusResponse;
import com.memento3d.payment.application.service.PaymentService;
import com.memento3d.shared.api.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/payments", "/api/payments"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment", description = "Online payment processing for 3D orders (MoMo, ZaloPay)")
public class PaymentController {

    private final PaymentService paymentService;
    private final com.memento3d.payment.infrastructure.persistence.repository.PaymentTransactionRepository paymentTransactionRepository;

    @GetMapping
    @Operation(
            summary = "List payment transactions",
            description = "Retrieves payment transactions for the authenticated user (or all transactions if Admin/Operator)",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    public ResponseEntity<java.util.List<PaymentStatusResponse>> listPayments(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails cud = getAuthenticatedUserDetails(userDetails);
        boolean isStaff = cud.getRole() == Role.ROLE_ADMIN 
                || cud.getRole() == Role.ROLE_MANAGER 
                || cud.getRole() == Role.ROLE_OPERATOR;
        
        java.util.List<com.memento3d.payment.domain.model.PaymentTransaction> txs;
        if (isStaff) {
            txs = paymentTransactionRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        } else {
            txs = paymentTransactionRepository.findByUserIdOrderByCreatedAtDesc(cud.getId());
        }

        java.util.List<PaymentStatusResponse> list = txs.stream().map(PaymentStatusResponse::fromEntity).toList();
        return ResponseEntity.ok(list);
    }

    @PostMapping
    @Operation(
            summary = "Create payment transaction",
            description = "Initiates a new payment transaction with MoMo or ZaloPay for an order owned by the user",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payment initiated successfully, redirect URL provided",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or invalid order status",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized JWT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "User does not own the requested order",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails cud = getAuthenticatedUserDetails(userDetails);
        log.info("Received payment creation request for orderId: {}, user: {}", request.getOrderId(), cud.getId());

        PaymentResponse response = paymentService.createPayment(request, cud.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{paymentId}")
    @Operation(
            summary = "Get payment transaction status",
            description = "Retrieves the status of a payment transaction. Reconciles with the provider if currently pending.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment status retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentStatusResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized JWT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "User not authorized to access this payment",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Payment transaction not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails cud = getAuthenticatedUserDetails(userDetails);
        log.info("Received request to get payment status for ID: {}, user: {}", paymentId, cud.getId());

        PaymentStatusResponse response = paymentService.getPaymentStatus(paymentId, cud.getId(), cud.getRole());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{paymentId}")
    @Operation(
            summary = "Cancel pending payment transaction",
            description = "Cancels a pending or processing payment transaction.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment cancelled successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentStatusResponse.class))),
            @ApiResponse(responseCode = "400", description = "Payment is in terminal status"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden: user does not own transaction"),
            @ApiResponse(responseCode = "404", description = "Payment transaction not found")
    })
    public ResponseEntity<PaymentStatusResponse> cancelPayment(
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CustomUserDetails cud = getAuthenticatedUserDetails(userDetails);
        log.info("Received request to cancel payment ID: {}, user: {}", paymentId, cud.getId());

        PaymentStatusResponse response = paymentService.cancelPayment(paymentId, cud.getId(), cud.getRole());
        return ResponseEntity.ok(response);
    }

    private CustomUserDetails getAuthenticatedUserDetails(CustomUserDetails userDetails) {
        if (userDetails != null) {
            return userDetails;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InvalidTokenException("No authenticated user in security context");
        }
        if (authentication.getPrincipal() instanceof CustomUserDetails cud) {
            return cud;
        }
        throw new InvalidTokenException("Invalid user details in security context");
    }
}
