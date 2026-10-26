package com.memento3d.notification.infrastructure.email;

import com.memento3d.orders.domain.event.OrderConfirmedEvent;
import com.memento3d.orders.domain.event.OrderCompletedEvent;
import com.memento3d.notification.service.OrderEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for order lifecycle events strictly AFTER database commit.
 * Asynchronously processes email notifications without delaying the main HTTP/database transactions.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEmailEventListener {

    private final OrderEmailService orderEmailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        log.info("OrderConfirmedEvent received AFTER_COMMIT for orderId={}", event.orderId());
        try {
            orderEmailService.processOrderConfirmed(event.orderId());
        } catch (Exception ex) {
            log.error("Error processing OrderConfirmedEvent for orderId={}: {}", event.orderId(), ex.getMessage(), ex);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCompleted(OrderCompletedEvent event) {
        log.info("OrderCompletedEvent received AFTER_COMMIT for orderId={}", event.orderId());
        try {
            orderEmailService.processOrderCompleted(event.orderId());
        } catch (Exception ex) {
            log.error("Error processing OrderCompletedEvent for orderId={}: {}", event.orderId(), ex.getMessage(), ex);
        }
    }
}
