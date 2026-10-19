package com.memento3d.admin.controller;

import com.memento3d.admin.dto.AdminPaymentTransactionResponse;
import com.memento3d.payment.domain.model.PaymentMethod;
import com.memento3d.payment.domain.model.PaymentStatus;
import com.memento3d.payment.domain.model.PaymentTransaction;
import com.memento3d.payment.infrastructure.persistence.repository.PaymentTransactionRepository;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Payments & Ledger", description = "Financial transactions monitoring, gateway verification, and audit logs")
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final PaymentTransactionRepository paymentTransactionRepository;

    @GetMapping("")
    @Operation(summary = "Monitor financial payment transactions across MoMo, ZaloPay, Cash, and Banking")
    public ResponseEntity<Page<AdminPaymentTransactionResponse>> listPayments(
            @RequestParam(name = "status", required = false) PaymentStatus status,
            @RequestParam(name = "paymentMethod", required = false) PaymentMethod paymentMethod,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PaymentTransaction> transactions = paymentTransactionRepository.findAllWithFilters(status, paymentMethod, pageRequest);
        return ResponseEntity.ok(transactions.map(AdminPaymentTransactionResponse::fromEntity));
    }
}
