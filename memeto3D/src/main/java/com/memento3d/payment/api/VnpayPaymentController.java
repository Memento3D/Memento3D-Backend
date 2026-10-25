package com.memento3d.payment.api;

import com.memento3d.identity.domain.exception.InvalidTokenException;
import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.payment.application.service.VnpayPaymentService;
import com.memento3d.payment.infrastructure.gateway.vnpay.dto.CreateVnpayPaymentRequest;
import com.memento3d.payment.infrastructure.gateway.vnpay.dto.CreateVnpayPaymentResponse;
import com.memento3d.payment.infrastructure.gateway.vnpay.dto.VnpayIpnResponse;
import com.memento3d.payment.infrastructure.gateway.vnpay.dto.VnpayReturnResponse;
import com.memento3d.shared.api.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/payments/vnpay", "/api/payments/vnpay"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "VNPAY Payment Gateway", description = "VNPAY Sandbox v2.1.0 checkout, return verification, and IPN webhook endpoints")
public class VnpayPaymentController {

    private final VnpayPaymentService vnpayPaymentService;

    @PostMapping("/create")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
            summary = "Create VNPAY payment URL",
            description = "Initiates a VNPAY payment session for an order owned by the user. " +
                          "Amount is strictly computed server-side from Order total price."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "VNPAY URL created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CreateVnpayPaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or order not eligible",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized JWT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "User does not own this order",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Order is already paid",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CreateVnpayPaymentResponse> createPayment(
            @Valid @RequestBody CreateVnpayPaymentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest servletRequest
    ) {
        CustomUserDetails cud = getAuthenticatedUserDetails(userDetails);
        String clientIp = extractClientIp(servletRequest);
        log.info("Received VNPAY payment creation request for orderId: {}, user: {}, ip: {}",
                request.getOrderId(), cud.getId(), clientIp);

        CreateVnpayPaymentResponse response = vnpayPaymentService.createPayment(request, cud.getId(), clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/return")
    @Operation(
            summary = "VNPAY return callback verification",
            description = "Public endpoint called by the frontend after VNPAY redirects the customer. " +
                          "Verifies HMAC-SHA512 secure hash, amounts, and returns clean payment result."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "VNPAY return verified successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = VnpayReturnResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid signature or amount mismatch",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Transaction not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<VnpayReturnResponse> handleVnpayReturn(
            @RequestParam Map<String, String> queryParams,
            HttpServletRequest request
    ) {
        Map<String, String> params = extractAllParams(request, queryParams);
        log.info("Received VNPAY return redirect with txnRef: {}", params.get("vnp_TxnRef"));

        VnpayReturnResponse response = vnpayPaymentService.processReturn(params);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ipn")
    @Operation(
            summary = "VNPAY IPN webhook (GET)",
            description = "Public server-to-server webhook invoked asynchronously by VNPAY. " +
                          "Verifies signature, ensures idempotency, and updates Order to PAID."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "IPN processed (returns VNPAY RspCode JSON)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = VnpayIpnResponse.class)))
    })
    public ResponseEntity<VnpayIpnResponse> handleVnpayIpnGet(
            @RequestParam Map<String, String> queryParams,
            HttpServletRequest request
    ) {
        Map<String, String> params = extractAllParams(request, queryParams);
        log.info("Received VNPAY IPN GET request with txnRef: {}", params.get("vnp_TxnRef"));

        VnpayIpnResponse response = vnpayPaymentService.processIpn(params);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/ipn")
    @Operation(
            summary = "VNPAY IPN webhook (POST)",
            description = "Public server-to-server webhook invoked asynchronously by VNPAY if configured via POST."
    )
    public ResponseEntity<VnpayIpnResponse> handleVnpayIpnPost(
            @RequestParam Map<String, String> queryParams,
            HttpServletRequest request
    ) {
        Map<String, String> params = extractAllParams(request, queryParams);
        log.info("Received VNPAY IPN POST request with txnRef: {}", params.get("vnp_TxnRef"));

        VnpayIpnResponse response = vnpayPaymentService.processIpn(params);
        return ResponseEntity.ok(response);
    }

    private Map<String, String> extractAllParams(HttpServletRequest request, Map<String, String> queryParams) {
        Map<String, String> params = new HashMap<>(queryParams);
        Enumeration<String> paramNames = request.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            if (!params.containsKey(paramName)) {
                params.put(paramName, request.getParameter(paramName));
            }
        }
        return params;
    }

    private String extractClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
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
