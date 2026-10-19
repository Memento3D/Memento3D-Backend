package com.memento3d.address.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A saved delivery address in the owner's address book.
 * <p>
 * Orders do NOT reference this table: they copy the formatted address into {@code orders.shipping_address}
 * at checkout (snapshot), so editing or deleting an address never rewrites order history.
 * The owner is referenced by plain id (no association) to keep queries free of N+1 / joins.
 */
@Entity
@Table(name = "user_addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    @Column(name = "province", nullable = false, length = 100)
    private String province;

    @Column(name = "ward", nullable = false, length = 100)
    private String ward;

    @Column(name = "street_address", nullable = false, length = 255)
    private String streetAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "label", nullable = false, length = 20)
    @Builder.Default
    private AddressLabel label = AddressLabel.HOME;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Single-line form suitable for an order's shipping-address snapshot. */
    public String formatted() {
        return streetAddress + ", " + ward + ", " + province;
    }
}
