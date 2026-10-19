package com.memento3d.pricing.api.response;

import com.memento3d.pricing.domain.model.QuoteLineItem;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class QuoteLineItemResponse {
    private UUID id;
    private String title;
    private BigDecimal amount;
    private String lineType;

    public static QuoteLineItemResponse fromEntity(QuoteLineItem item) {
        return QuoteLineItemResponse.builder()
                .id(item.getId())
                .title(item.getTitle())
                .amount(item.getAmount())
                .lineType(item.getLineType())
                .build();
    }
}
