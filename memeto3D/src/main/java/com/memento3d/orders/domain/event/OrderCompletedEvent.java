package com.memento3d.orders.domain.event;

import java.util.UUID;

/**
 * Domain event published when an order transitions to DELIVERED or COMPLETED.
 */
public record OrderCompletedEvent(UUID orderId) {
}
