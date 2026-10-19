package com.memento3d.quotation.api;

import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.quotation.application.service.ManualQuotationService;
import com.memento3d.quotation.domain.model.ManualQuotation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/quotations/manual")
@RequiredArgsConstructor
@Tag(name = "Manual Quotation", description = "Manual review quotation endpoints for premium and custom physical orders")
public class ManualQuotationController {

    private final ManualQuotationService manualQuotationService;

    @Getter
    @Setter
    public static class CreateQuotationRequest {
        @NotNull(message = "Scan ID is required")
        private UUID scanId;

        private UUID productId;

        @NotBlank(message = "Customization specifications are required")
        private String specifications;

        private String customerNotes;
    }

    @Getter
    @Setter
    public static class ManagerQuoteRequest {
        @NotNull(message = "Quoted amount is required")
        @DecimalMin(value = "0.00", message = "Quoted amount must be non-negative")
        private BigDecimal quotedAmount;

        private String notes;

        private Integer validityHours;
    }

    @Getter
    @Setter
    @Builder
    public static class QuotationResponse {
        private UUID id;
        private UUID userId;
        private UUID scanId;
        private UUID productId;
        private String customizationSpec;
        private String customerNotes;
        private String status;
        private BigDecimal quotedAmount;
        private String managerNotes;
        private UUID assignedManagerId;
        private Instant validUntil;
        private Long version;
        private Instant createdAt;
        private Instant updatedAt;

        public static QuotationResponse from(ManualQuotation q) {
            return QuotationResponse.builder()
                    .id(q.getId())
                    .userId(q.getUserId())
                    .scanId(q.getScanId())
                    .productId(q.getProductId())
                    .customizationSpec(q.getCustomizationSpec())
                    .customerNotes(q.getCustomerNotes())
                    .status(q.getStatus())
                    .quotedAmount(q.getQuotedAmount())
                    .managerNotes(q.getManagerNotes())
                    .assignedManagerId(q.getAssignedManagerId())
                    .validUntil(q.getValidUntil())
                    .version(q.getVersion())
                    .createdAt(q.getCreatedAt())
                    .updatedAt(q.getUpdatedAt())
                    .build();
        }
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Submit a manual quotation request")
    public ResponseEntity<QuotationResponse> submitRequest(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody CreateQuotationRequest request) {
        ManualQuotation quotation = ManualQuotation.builder()
                .scanId(request.getScanId())
                .productId(request.getProductId())
                .customizationSpec(request.getSpecifications())
                .customerNotes(request.getCustomerNotes())
                .build();

        ManualQuotation created = manualQuotationService.submitRequest(quotation, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(QuotationResponse.from(created));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get quotation by ID")
    public ResponseEntity<QuotationResponse> getQuotation(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable UUID id) {
        ManualQuotation quotation = manualQuotationService.getById(id, currentUser.getId(), currentUser.getRole());
        return ResponseEntity.ok(QuotationResponse.from(quotation));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List customer manual quotations")
    public ResponseEntity<List<QuotationResponse>> getMyQuotations(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        List<QuotationResponse> list = manualQuotationService.getMyQuotations(currentUser.getId())
                .stream().map(QuotationResponse::from).toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/admin/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Manager: List pending quotations")
    public ResponseEntity<List<QuotationResponse>> listPending() {
        List<QuotationResponse> list = manualQuotationService.getPendingQuotations()
                .stream().map(QuotationResponse::from).toList();
        return ResponseEntity.ok(list);
    }

    @PatchMapping("/admin/{id}/quote")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Manager: Provide formal quotation with validity period")
    public ResponseEntity<QuotationResponse> provideQuote(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody ManagerQuoteRequest request) {
        int hours = request.getValidityHours() != null ? request.getValidityHours() : 72;
        Instant validUntil = Instant.now().plus(hours, ChronoUnit.HOURS);

        ManualQuotation quotation = manualQuotationService.provideQuote(
                id, request.getQuotedAmount(), validUntil, request.getNotes(), currentUser.getId());
        return ResponseEntity.ok(QuotationResponse.from(quotation));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Customer: Accept quotation")
    public ResponseEntity<QuotationResponse> acceptQuotation(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable UUID id) {
        ManualQuotation quotation = manualQuotationService.acceptQuote(id, currentUser.getId());
        return ResponseEntity.ok(QuotationResponse.from(quotation));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Customer: Reject quotation")
    public ResponseEntity<QuotationResponse> rejectQuotation(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable UUID id) {
        ManualQuotation quotation = manualQuotationService.rejectQuote(id, currentUser.getId());
        return ResponseEntity.ok(QuotationResponse.from(quotation));
    }
}
