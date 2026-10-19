package com.memento3d.shared.outbox.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.shared.outbox.domain.model.TransactionalOutbox;
import com.memento3d.shared.outbox.infrastructure.persistence.repository.TransactionalOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherService {

    private final TransactionalOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String aggregateType, String aggregateId, String eventType, Object payload) {
        try {
            String payloadJson = payload instanceof String ? (String) payload : objectMapper.writeValueAsString(payload);
            TransactionalOutbox outbox = TransactionalOutbox.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(payloadJson)
                    .status("PENDING")
                    .retryCount(0)
                    .build();
            outboxRepository.save(outbox);
            log.info("Saved outbox event: aggregateType={}, aggregateId={}, eventType={}", aggregateType, aggregateId, eventType);
        } catch (Exception e) {
            log.error("Failed to serialize outbox event: {}", e.getMessage(), e);
            throw new RuntimeException("Could not persist outbox event", e);
        }
    }
}
