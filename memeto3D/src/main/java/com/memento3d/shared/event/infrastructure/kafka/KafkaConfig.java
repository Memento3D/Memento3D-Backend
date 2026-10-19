package com.memento3d.shared.event.infrastructure.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {

    @Value("${app.kafka.topics.reconstruction:memento3d.reconstruction}")
    private String reconstructionTopic;

    @Value("${app.kafka.topics.orders:memento3d.orders}")
    private String ordersTopic;

    @Value("${app.kafka.topics.payments:memento3d.payments}")
    private String paymentsTopic;

    @Value("${app.kafka.topics.printjobs:memento3d.printjobs}")
    private String printjobsTopic;

    @Value("${app.kafka.topics.quality:memento3d.quality}")
    private String qualityTopic;

    @Value("${app.kafka.topics.notifications:memento3d.notifications}")
    private String notificationsTopic;

    @Value("${app.kafka.topics.dlt:memento3d.dlt}")
    private String dltTopic;

    @Bean
    public NewTopic reconstructionTopic() {
        return TopicBuilder.name(reconstructionTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic ordersTopic() {
        return TopicBuilder.name(ordersTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentsTopic() {
        return TopicBuilder.name(paymentsTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic printjobsTopic() {
        return TopicBuilder.name(printjobsTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic qualityTopic() {
        return TopicBuilder.name(qualityTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic notificationsTopic() {
        return TopicBuilder.name(notificationsTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic dltTopic() {
        return TopicBuilder.name(dltTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public ProducerFactory<String, String> producerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public CommonErrorHandler commonErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(dltTopic, 0));
        // Retry 3 times with 1000ms delay, then send to DLT
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    }
}
