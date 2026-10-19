package com.memento3d.pricing.api;

import com.memento3d.identity.infrastructure.security.CustomUserDetails;
import com.memento3d.pricing.api.request.QuoteRequest;
import com.memento3d.pricing.api.response.QuoteResponse;
import com.memento3d.pricing.application.service.PricingEngineService;
import com.memento3d.pricing.domain.model.PricingRule;
import io.swagger.v3.oas.annotations.Operation;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Pricing Engine", description = "Server-authoritative price quotes, rule calculations, and quote snapshots")
@SecurityRequirement(name = "BearerAuth")
public class PricingController {

    private final PricingEngineService pricingEngineService;

    @PostMapping("/api/v1/pricing/quotes")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Generate price quote", description = "Calculates exact quote based on product, size, customization, and shipping")
    public ResponseEntity<QuoteResponse> generateQuote(
            @Valid @RequestBody QuoteRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        QuoteResponse response = pricingEngineService.generateQuote(request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/v1/pricing/quotes/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get price quote by ID", description = "Retrieves quote details and line-item breakdown")
    public ResponseEntity<QuoteResponse> getQuote(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(pricingEngineService.getQuoteById(id, userDetails.getId(), userDetails.getRole()));
    }

    @GetMapping("/api/v1/admin/pricing/rules")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "List all pricing rules", description = "Admin/Manager only. Retrieves active and inactive pricing rules.")
    public ResponseEntity<List<PricingRule>> getRules() {
        return ResponseEntity.ok(pricingEngineService.getAllRules());
    }

    @PostMapping("/api/v1/admin/pricing/rules")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Create or update pricing rule", description = "Admin/Manager only. Configures new dynamic price modifier rule.")
    public ResponseEntity<PricingRule> saveRule(@Valid @RequestBody PricingRule rule) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pricingEngineService.saveRule(rule));
    }
}
