package org.example.agapibeassignment.application.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.agapibeassignment.application.entity.InventoryEvent;
import org.example.agapibeassignment.application.repository.*;
import org.example.agapibeassignment.infrastructure.config.KafkaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Kafka consumer implementing Saga compensation pattern.
 *
 * On SUCCESS: deduct warehouse stock → confirm order (PENDING → SUCCESS)
 * On FAILURE: compensate ALL purchase steps:
 * - Restore user balance
 * - Decrement sold_quantity
 * - Restore Redis flash sale stock
 * - Cancel order (PENDING → CANCELLED)
 *
 * Idempotent: event record with unique eventId prevents duplicate processing.
 * Multi-pod safe: all operations are atomic DB queries, no in-memory state.
 */
@Component
public class InventorySyncConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventorySyncConsumer.class);
    private static final String STOCK_KEY = "flash_sale_stock:";
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final InventoryEventRepository eventRepo;
    private final ProductRepository productRepo;
    private final UserRepository userRepo;
    private final FlashSaleItemRepository itemRepo;
    private final FlashSaleOrderRepository orderRepo;
    private final RedisTemplate<String, Object> redis;
    private final TransactionTemplate txTemplate;

    public InventorySyncConsumer(InventoryEventRepository eventRepo, ProductRepository productRepo,
            UserRepository userRepo, FlashSaleItemRepository itemRepo,
            FlashSaleOrderRepository orderRepo,
            RedisTemplate<String, Object> redis,
            TransactionTemplate txTemplate) {
        this.eventRepo = eventRepo;
        this.productRepo = productRepo;
        this.userRepo = userRepo;
        this.itemRepo = itemRepo;
        this.orderRepo = orderRepo;
        this.redis = redis;
        this.txTemplate = txTemplate;
        log.info("🚀 InventorySyncConsumer bean initialized");
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_INVENTORY_SYNC)
    public void consume(String message) {
        log.info("📥 [Kafka Consumer] Received message: {}", message);
        JsonNode json;
        try {
            json = objectMapper.readTree(message);
        } catch (Exception e) {
            log.error("❌ Failed to parse Kafka message: {}", message, e);
            return;
        }

        String eventId = json.get("eventId").asText();
        String eventType = json.get("type").asText();
        Long productId = json.get("productId").asLong();
        int quantity = json.get("quantity").asInt();
        Long itemId = json.get("itemId").asLong();
        Long userId = json.get("userId").asLong();
        BigDecimal amount = new BigDecimal(json.get("amount").asText());
        String orderNo = json.get("orderNo").asText();

        // Idempotency: skip if already processed or compensated
        if (isAlreadyHandled(eventId)) {
            log.info("Event already handled, skipping: eventId={}", eventId);
            return;
        }

        // Claim event
        if (!claimEvent(eventId, eventType, productId, quantity)) {
            return;
        }

        // Try to deduct warehouse stock
        try {
            txTemplate.executeWithoutResult(status -> {
                int stockUpdated = productRepo.deductStockStrict(productId, quantity);

                if (stockUpdated == 0) {
                    // Warehouse stock insufficient → trigger compensation
                    log.warn("⚠️ Warehouse stock insufficient for product {}. Initiating saga compensation.",
                            productId);
                    eventRepo.updateStatusByEventId(eventId, "COMPENSATION_NEEDED", LocalDateTime.now());
                    throw new SagaCompensationException("Warehouse stock insufficient");
                }

                // Stock deducted successfully → confirm order
                orderRepo.updateStatusByOrderNo(orderNo, "SUCCESS");
                eventRepo.updateStatusByEventId(eventId, "PROCESSED", LocalDateTime.now());
            });

            log.info("✅ Saga SUCCESS: orderNo={}, productId={}, warehouse stock deducted", orderNo, productId);

        } catch (SagaCompensationException e) {
            // Expected: warehouse stock insufficient → compensate
            compensate(eventId, itemId, userId, amount, orderNo);
        } catch (Exception e) {
            // Unexpected error (DB timeout, connection lost, etc.) → compensate
            log.error("❌ Unexpected error during stock deduction. Initiating compensation.", e);
            compensate(eventId, itemId, userId, amount, orderNo);
        }
    }

    /**
     * Saga compensation — rollback ALL purchase steps.
     * Each step is atomic and idempotent. Safe to call multiple times.
     */
    private void compensate(String eventId, Long itemId, Long userId, BigDecimal amount, String orderNo) {
        try {
            txTemplate.executeWithoutResult(status -> {
                // 1. Delete order — frees UNIQUE(user_id, sale_date) so user can retry
                orderRepo.deleteByOrderNo(orderNo);

                // 2. Restore user balance
                userRepo.restoreBalance(userId, amount);

                // 3. Decrement sold_quantity
                itemRepo.decrementSoldQuantity(itemId);

                // 4. Mark event as compensated
                eventRepo.updateStatusByEventId(eventId, "COMPENSATED", LocalDateTime.now());
            });

            // 5. Restore Redis flash sale stock (outside DB transaction)
            redis.opsForValue().increment(STOCK_KEY + itemId);

            log.info("🔄 Saga COMPENSATED: orderNo={}, userId={}, itemId={}, amount={} restored",
                    orderNo, userId, itemId, amount);

        } catch (Exception e) {
            log.error("❌ CRITICAL: Compensation FAILED for orderNo={}. Manual intervention required! " +
                    "eventId={}, userId={}, itemId={}, amount={}",
                    orderNo, eventId, userId, itemId, amount, e);
            // Re-throw so DefaultErrorHandler retries, then routes to DLT
            throw new RuntimeException("Compensation failed for order: " + orderNo, e);
        }
    }

    private boolean isAlreadyHandled(String eventId) {
        return eventRepo.existsByEventIdAndStatus(eventId, "PROCESSED")
                || eventRepo.existsByEventIdAndStatus(eventId, "COMPENSATED");
    }

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
            if (isAlreadyHandled(eventId)) {
                log.info("Event already handled by another consumer: eventId={}", eventId);
                return false;
            }
            log.info("Re-processing event: eventId={}", eventId);
            return true;
        }
    }

    /**
     * Internal exception to signal that compensation is needed.
     */
    private static class SagaCompensationException extends RuntimeException {
        SagaCompensationException(String message) {
            super(message);
        }
    }
}
