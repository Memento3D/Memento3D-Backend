package com.memento3d.orders.domain.model;

import com.memento3d.payment.domain.model.PaymentMethod;
import com.memento3d.payment.domain.model.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "print_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "order_code", unique = true, length = 100)
    private String orderCode;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "scan_id")
    private UUID scanId;

    @Column(name = "subtotal", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "shipping_fee", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 30)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 30)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "recipient_name", length = 255)
    private String recipientName;

    @Column(name = "shipping_address", columnDefinition = "TEXT")
    private String shippingAddress;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @org.hibernate.annotations.BatchSize(size = 50)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

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

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public boolean isEligibleForPayment() {
        return (this.status == OrderStatus.PENDING || this.status == OrderStatus.PENDING_PAYMENT)
                && this.paymentStatus != PaymentStatus.PAID
                && this.paymentStatus != PaymentStatus.SUCCESS;
    }

    public void markAsPaid() {
        this.paymentStatus = PaymentStatus.PAID;
        if (this.status == OrderStatus.PENDING || this.status == OrderStatus.PENDING_PAYMENT) {
            this.status = OrderStatus.CONFIRMED;
        }
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
        if (this.paymentStatus == PaymentStatus.PENDING) {
            this.paymentStatus = PaymentStatus.CANCELLED;
        }
    }

    /**
     * Validates valid order status transitions:
     * PENDING -> CONFIRMED -> PROCESSING -> PRINTING -> READY -> COMPLETED / DELIVERED
     * Any non-terminal state can be CANCELLED (if not completed).
     */
    public boolean canTransitionTo(OrderStatus nextStatus) {
        if (nextStatus == null || nextStatus == this.status) return false;
        if (this.status == OrderStatus.COMPLETED || this.status == OrderStatus.DELIVERED || this.status == OrderStatus.CANCELLED) {
            return false;
        }
        if (nextStatus == OrderStatus.CANCELLED) {
            return true;
        }
        return switch (this.status) {
            case PENDING, PENDING_PAYMENT -> nextStatus == OrderStatus.CONFIRMED;
            case CONFIRMED -> nextStatus == OrderStatus.PROCESSING;
            case PROCESSING -> nextStatus == OrderStatus.PRINTING;
            case PRINTING -> nextStatus == OrderStatus.READY;
            case READY -> nextStatus == OrderStatus.COMPLETED || nextStatus == OrderStatus.SHIPPED || nextStatus == OrderStatus.DELIVERED;
            case SHIPPED -> nextStatus == OrderStatus.DELIVERED || nextStatus == OrderStatus.COMPLETED;
            default -> false;
        };
    }
}
