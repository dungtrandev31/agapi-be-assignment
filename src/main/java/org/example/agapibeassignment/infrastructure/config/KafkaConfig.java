package org.example.agapibeassignment.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {
    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    public static final String TOPIC_INVENTORY_SYNC = "flashsale.inventory.sync";
    public static final String TOPIC_INVENTORY_DLT = "flashsale.inventory.sync.dlt";

    @Bean
    public NewTopic inventorySyncTopic() {
        return TopicBuilder.name(TOPIC_INVENTORY_SYNC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic inventoryDltTopic() {
        return TopicBuilder.name(TOPIC_INVENTORY_DLT).partitions(1).replicas(1).build();
    }

    /**
     * Error handler with retry + Dead Letter Topic routing.
     * - Retries 3 times with 1-second interval
     * - After all retries exhausted, publishes to DLT for manual investigation
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> {
                    log.error("Message exhausted retries, routing to DLT: topic={}, key={}, error={}",
                            record.topic(), record.key(), ex.getMessage());
                    return new TopicPartition(TOPIC_INVENTORY_DLT, -1);
                });

        // 3 retries, 1 second apart. After exhaustion → DLT
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));

        // Don't retry on these non-transient exceptions
        errorHandler.addNotRetryableExceptions(
                com.fasterxml.jackson.core.JsonProcessingException.class);

        return errorHandler;
    }
}