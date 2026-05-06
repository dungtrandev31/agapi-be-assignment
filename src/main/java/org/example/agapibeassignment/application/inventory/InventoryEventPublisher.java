package org.example.agapibeassignment.application.inventory;

import org.example.agapibeassignment.infrastructure.config.KafkaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Publishes inventory events to Kafka ONLY AFTER purchase transaction commits.
 * Message contains full saga context so the consumer can:
 * - Deduct warehouse stock on success
 * - Compensate (rollback) all purchase steps on failure
 */
@Component
public class InventoryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventPublisher.class);
    private final KafkaTemplate<String, String> kafkaTemplate;

    public InventoryEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInventoryDeduction(InventoryDeductionEvent event) {
        String payload = String.format(
                "{\"eventId\":\"%s\",\"type\":\"STOCK_DEDUCTED\"," +
                        "\"productId\":%d,\"quantity\":%d," +
                        "\"itemId\":%d,\"userId\":%d,\"amount\":%s,\"orderNo\":\"%s\"}",
                UUID.randomUUID(),
                event.getProductId(), event.getQuantity(),
                event.getItemId(), event.getUserId(),
                event.getAmount().toPlainString(), event.getOrderNo());

        kafkaTemplate.send(KafkaConfig.TOPIC_INVENTORY_SYNC,
                String.valueOf(event.getProductId()), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("⚠️ CRITICAL: Failed to send inventory event to Kafka. " +
                                "Order {} may stay PENDING. Manual intervention needed. Error: {}",
                                event.getOrderNo(), ex.getMessage());
                    } else {
                        log.info("Inventory event sent: orderNo={}, productId={}",
                                event.getOrderNo(), event.getProductId());
                    }
                });
    }
}
