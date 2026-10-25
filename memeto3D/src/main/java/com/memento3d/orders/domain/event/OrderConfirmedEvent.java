package com.memento3d.orders.domain.event;

import java.util.UUID;

/**
 * Domain event published when an order transitions to CONFIRMED.
 */
public record OrderConfirmedEvent(UUID orderId) {
}
