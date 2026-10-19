package com.memento3d.pricing.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "price_quotes",
        indexes = {
                @Index(name = "idx_price_quotes_user", columnList = "user_id"),
                @Index(name = "idx_price_quotes_validity", columnList = "valid_until, is_used")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "base_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal baseAmount = BigDecimal.ZERO;

    @Column(name = "customization_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal customizationAmount = BigDecimal.ZERO;

    @Column(name = "shipping_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal shippingAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "VND";

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    @Column(name = "is_used", nullable = false)
    @Builder.Default
    private Boolean isUsed = false;

    @Column(name = "configuration_snapshot", columnDefinition = "TEXT")
    private String configurationSnapshot;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<QuoteLineItem> lineItems = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isValid() {
        return !isUsed && validUntil != null && validUntil.isAfter(Instant.now());
    }

    public void addLineItem(QuoteLineItem item) {
        lineItems.add(item);
        item.setQuote(this);
    }
}
