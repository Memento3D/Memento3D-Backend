package com.memento3d.shared.event.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.shared.event.model.RealTimeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class KafkaEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaEventPublisher(
            @Autowired(required = false) KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(String topic, RealTimeEvent event) {
        if (kafkaTemplate == null) {
            log.debug("KafkaTemplate is not available. Skipping publish for topic={}, eventId={}", topic, event.getEventId());
            return;
        }
        try {
            String key = event.getResourceId() != null ? event.getResourceId() : event.getEventId().toString();
            String payload = objectMapper.writeValueAsString(event);

            // Wait synchronously with a timeout to confirm message delivery to broker
            kafkaTemplate.send(topic, key, payload).get(3, TimeUnit.SECONDS);

            log.info("Published event to Kafka: topic={}, eventId={}, type={}", topic, event.getEventId(), event.getType());
        } catch (Exception e) {
            log.error("Failed to publish event to Kafka [topic={}, eventId={}]: {}", topic, event.getEventId(), e.getMessage());
            throw new RuntimeException("Kafka publish failed for topic " + topic, e);
        }
    }
}
