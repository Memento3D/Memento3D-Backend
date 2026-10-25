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
import com.memento3d.shared.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    @Operation(summary = "Monitor financial payment transactions across VNPAY, MoMo, ZaloPay, and Cash")
    public ResponseEntity<Page<AdminPaymentTransactionResponse>> listPayments(
            @RequestParam(name = "status", required = false) PaymentStatus status,
            @RequestParam(name = "paymentMethod", required = false) PaymentMethod paymentMethod,
            @RequestParam(name = "transactionReference", required = false) String transactionReference,
            @RequestParam(name = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFrom,
            @RequestParam(name = "dateTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateTo,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<PaymentTransaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (paymentMethod != null) {
                predicates.add(cb.equal(root.get("paymentMethod"), paymentMethod));
            }
            if (transactionReference != null && !transactionReference.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("merchantTransactionId")), "%" + transactionReference.trim().toLowerCase() + "%"));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), dateTo));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<PaymentTransaction> transactions = paymentTransactionRepository.findAll(spec, pageRequest);
        return ResponseEntity.ok(transactions.map(AdminPaymentTransactionResponse::fromEntity));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment transaction detail by ID for Admin audit")
    public ResponseEntity<AdminPaymentTransactionResponse> getPaymentById(@PathVariable UUID id) {
        PaymentTransaction tx = paymentTransactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment transaction not found with ID: " + id));
        return ResponseEntity.ok(AdminPaymentTransactionResponse.fromEntity(tx));
    }
}
