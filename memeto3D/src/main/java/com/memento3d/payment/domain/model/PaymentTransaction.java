package com.memento3d.payment.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Column(name = "provider", nullable = false, length = 30)
    private String provider;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "merchant_transaction_id", nullable = false, unique = true, length = 100)
    private String merchantTransactionId;

    @Column(name = "provider_transaction_id", length = 255)
    private String providerTransactionId;

    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    @Column(name = "payment_url", columnDefinition = "TEXT")
    private String paymentUrl;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "failure_message", columnDefinition = "TEXT")
    private String failureMessage;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isTerminal() {
        return this.status == PaymentStatus.SUCCESS
                || this.status == PaymentStatus.FAILED
                || this.status == PaymentStatus.CANCELLED
                || this.status == PaymentStatus.EXPIRED;
    }

    public void markSuccess(String providerTransactionId, Instant paidAt) {
        this.status = PaymentStatus.SUCCESS;
        this.providerTransactionId = providerTransactionId;
        this.paidAt = paidAt != null ? paidAt : Instant.now();
        this.failureCode = null;
        this.failureMessage = null;
    }

    public void markFailed(String failureCode, String failureMessage) {
        this.status = PaymentStatus.FAILED;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }

    public void markCancelled(String reason) {
        this.status = PaymentStatus.CANCELLED;
        this.failureMessage = reason;
    }

    public void markExpired() {
        this.status = PaymentStatus.EXPIRED;
    }
}
