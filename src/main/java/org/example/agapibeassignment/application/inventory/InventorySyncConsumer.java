package org.example.agapibeassignment.application.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.agapibeassignment.application.entity.InventoryEvent;
import org.example.agapibeassignment.application.repository.InventoryEventRepository;
import org.example.agapibeassignment.application.repository.ProductRepository;
import org.example.agapibeassignment.infrastructure.config.KafkaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * Kafka consumer for inventory sync events.
 *
 * Design:
 *   1. Claim event (save as PROCESSING) in auto-committed transaction
 *   2. Update stock + mark PROCESSED in a single transaction (TransactionTemplate)
 *
 * Rollback safety:
 *   - If step 2 fails, event stays PROCESSING → retry will re-process
 *   - @Transactional is NOT on this method (intentional) — prevents rollback from wiping the event record
 *
 * Idempotency:
 *   - Unique constraint on eventId prevents duplicate INSERT (race condition safe)
 *   - Status check (PROCESSED) skips already-completed events
 *
 * Error handling:
 *   - DefaultErrorHandler retries 3 times with 1s backoff
 *   - After exhaustion → Dead Letter Topic (flashsale.inventory.sync.dlt)
 */
@Component
public class InventorySyncConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventorySyncConsumer.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final InventoryEventRepository eventRepo;
    private final ProductRepository productRepo;
    private final TransactionTemplate txTemplate;

    public InventorySyncConsumer(InventoryEventRepository eventRepo, ProductRepository productRepo,
                                 TransactionTemplate txTemplate) {
        this.eventRepo = eventRepo;
        this.productRepo = productRepo;
        this.txTemplate = txTemplate;
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_INVENTORY_SYNC, groupId = "flashsale-inventory-group")
    public void consume(String message) {
        JsonNode json;
        try {
            json = objectMapper.readTree(message);
        } catch (Exception e) {
            // Bad JSON — non-retryable, will go straight to DLT
            throw new RuntimeException("Invalid JSON message: " + message, e);
        }

        String eventId = json.get("eventId").asText();
        String eventType = json.get("type").asText();
        Long productId = json.get("productId").asLong();
        int quantity = json.get("quantity").asInt();

        // Step 1: Idempotency — skip if already fully processed
        if (eventRepo.existsByEventIdAndStatus(eventId, "PROCESSED")) {
            log.info("Event already processed, skipping: eventId={}", eventId);
            return;
        }

        // Step 2: Claim event — save as PROCESSING (auto-committed, survives later rollback)
        if (!claimEvent(eventId, eventType, productId, quantity)) {
            return; // Already processed by another consumer
        }

        // Step 3: Atomic stock update + mark PROCESSED in a single transaction
        // If this fails → event stays PROCESSING → retry will re-process (safe)
        txTemplate.executeWithoutResult(status -> {
            if ("STOCK_DEDUCTED".equals(eventType)) {
                int updated = productRepo.deductStock(productId, quantity);
                if (updated == 0) {
                    log.warn("⚠️ STOCK ALERT: Product {} not found or stock already 0", productId);
                }
            } else if ("STOCK_RESTORED".equals(eventType)) {
                int updated = productRepo.restoreStock(productId, quantity);
                if (updated == 0) {
                    log.warn("Product {} not found for stock restore", productId);
                }
            }

            // Mark as PROCESSED in the same transaction — atomic with stock update
            eventRepo.updateStatusByEventId(eventId, "PROCESSED", LocalDateTime.now());
        });

        log.info("Inventory synced: eventId={}, type={}, productId={}, qty={}",
                eventId, eventType, productId, quantity);
    }

    /**
     * Claim an event by saving it as PROCESSING.
     * Uses unique constraint on eventId to handle race conditions.
     *
     * @return true if claimed (should process), false if already PROCESSED (skip)
     */
    private boolean claimEvent(String eventId, String eventType, Long productId, int quantity) {
        try {
            InventoryEvent event = InventoryEvent.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .productId(productId)
                    .quantityChange(quantity)
                    .status("PROCESSING")
                    .build();
            eventRepo.save(event);
            return true;
        } catch (DataIntegrityViolationException e) {
            // Unique constraint violation — another consumer already claimed this event
            if (eventRepo.existsByEventIdAndStatus(eventId, "PROCESSED")) {
                log.info("Event already processed by another consumer: eventId={}", eventId);
                return false;
            }
            // Event exists but is PROCESSING or FAILED — safe to re-process
            log.info("Re-processing event (status not PROCESSED): eventId={}", eventId);
            return true;
        }
    }
}
