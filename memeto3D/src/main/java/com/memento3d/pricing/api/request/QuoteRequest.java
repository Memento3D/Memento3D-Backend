package com.memento3d.pricing.api.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteRequest {

    @NotNull(message = "productId is required")
    private UUID productId;

    private UUID materialId;

    private Integer sizeCm;

    private Map<String, Object> customization;

    @Builder.Default
    private boolean shippingRequired = true;

    private String shippingAddress;
}
