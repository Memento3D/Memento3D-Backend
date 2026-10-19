package com.memento3d.pricing.api.response;

import com.memento3d.pricing.domain.model.PriceQuote;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class QuoteResponse {
    private UUID id;
    private UUID userId;
    private UUID productId;
    private BigDecimal baseAmount;
    private BigDecimal customizationAmount;
    private BigDecimal shippingAmount;
    private BigDecimal totalAmount;
    private String currency;
    private Instant validUntil;
    private Boolean isUsed;
    private List<QuoteLineItemResponse> lineItems;
    private Instant createdAt;

    public static QuoteResponse fromEntity(PriceQuote quote) {
        List<QuoteLineItemResponse> items = quote.getLineItems() != null
                ? quote.getLineItems().stream().map(QuoteLineItemResponse::fromEntity).toList()
                : List.of();

        return QuoteResponse.builder()
                .id(quote.getId())
                .userId(quote.getUserId())
                .productId(quote.getProductId())
                .baseAmount(quote.getBaseAmount())
                .customizationAmount(quote.getCustomizationAmount())
                .shippingAmount(quote.getShippingAmount())
                .totalAmount(quote.getTotalAmount())
                .currency(quote.getCurrency())
                .validUntil(quote.getValidUntil())
                .isUsed(quote.getIsUsed())
                .lineItems(items)
                .createdAt(quote.getCreatedAt())
                .build();
    }
}
