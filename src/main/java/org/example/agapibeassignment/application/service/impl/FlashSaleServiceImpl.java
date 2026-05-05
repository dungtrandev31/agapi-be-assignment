package org.example.agapibeassignment.application.service.impl;

import org.example.agapibeassignment.application.common.exception.BusinessException;
import org.example.agapibeassignment.application.common.exception.ErrorCode;
import org.example.agapibeassignment.application.entity.*;
import org.example.agapibeassignment.application.repository.*;
import org.example.agapibeassignment.application.service.FlashSaleService;
import org.example.agapibeassignment.infrastructure.config.KafkaConfig;
import org.example.agapibeassignment.rest.response.FlashSaleItemResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FlashSaleServiceImpl implements FlashSaleService {
    private static final Logger log = LoggerFactory.getLogger(FlashSaleServiceImpl.class);
    private static final String STOCK_KEY = "flash_sale_stock:";

    private final FlashSaleSlotRepository slotRepo;
    private final FlashSaleItemRepository itemRepo;
    private final FlashSaleOrderRepository orderRepo;
    private final ProductRepository productRepo;
    private final UserRepository userRepo;
    private final RedisTemplate<String, Object> redis;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public FlashSaleServiceImpl(FlashSaleSlotRepository slotRepo, FlashSaleItemRepository itemRepo,
                                FlashSaleOrderRepository orderRepo, ProductRepository productRepo,
                                UserRepository userRepo, RedisTemplate<String, Object> redis,
                                KafkaTemplate<String, String> kafkaTemplate) {
        this.slotRepo = slotRepo;
        this.itemRepo = itemRepo;
        this.orderRepo = orderRepo;
        this.productRepo = productRepo;
        this.userRepo = userRepo;
        this.redis = redis;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public List<FlashSaleItemResponse> getCurrentFlashSaleItems() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        List<FlashSaleSlot> activeSlots = slotRepo.findActiveSlots(today, now);
        if (activeSlots.isEmpty()) return Collections.emptyList();

        List<Long> slotIds = activeSlots.stream().map(FlashSaleSlot::getId).toList();
        List<FlashSaleItem> items = itemRepo.findBySlotIdIn(slotIds);
        if (items.isEmpty()) return Collections.emptyList();

        // Batch load products
        Set<Long> productIds = items.stream().map(FlashSaleItem::getProductId).collect(Collectors.toSet());
        Map<Long, Product> productMap = productRepo.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        Map<Long, FlashSaleSlot> slotMap = activeSlots.stream()
                .collect(Collectors.toMap(FlashSaleSlot::getId, s -> s));

        return items.stream().map(item -> {
            Product product = productMap.get(item.getProductId());
            FlashSaleSlot slot = slotMap.get(item.getSlotId());
            int available = item.getSaleQuantity() - item.getSoldQuantity();

            return FlashSaleItemResponse.builder()
                    .itemId(item.getId())
                    .productId(item.getProductId())
                    .productName(product != null ? product.getName() : "Unknown")
                    .productDescription(product != null ? product.getDescription() : "")
                    .originalPrice(product != null ? product.getOriginalPrice() : null)
                    .flashPrice(item.getFlashPrice())
                    .availableQuantity(Math.max(0, available))
                    .slotTime(slot != null ? slot.getStartTime() + " - " + slot.getEndTime() : "")
                    .build();
        }).toList();
    }

    @Override
    @Transactional
    public String purchase(Long userId, Long itemId) {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        // 1. Check: user already purchased today?
        if (orderRepo.existsByUserIdAndSaleDate(userId, today))
            throw new BusinessException(ErrorCode.FLASH_SALE_ALREADY_PURCHASED);

        // 2. Validate flash sale item exists
        FlashSaleItem item = itemRepo.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FLASH_SALE_ITEM_NOT_FOUND));

        // 3. Validate: slot is currently active
        FlashSaleSlot slot = slotRepo.findById(item.getSlotId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FLASH_SALE_SLOT_NOT_FOUND));
        if (!slot.getSaleDate().equals(today) || now.isBefore(slot.getStartTime()) || !now.isBefore(slot.getEndTime()))
            throw new BusinessException(ErrorCode.FLASH_SALE_NOT_ACTIVE);

        // 4. Redis atomic DECR for fast stock check
        String stockKey = STOCK_KEY + itemId;
        Long remaining = redis.opsForValue().decrement(stockKey);
        if (remaining == null || remaining < 0) {
            // Rollback Redis
            redis.opsForValue().increment(stockKey);
            throw new BusinessException(ErrorCode.FLASH_SALE_SOLD_OUT);
        }

        try {
            // 5. Atomic DB update: increment sold_quantity (returns 0 if oversold)
            int updated = itemRepo.incrementSoldQuantity(itemId);
            if (updated == 0) {
                redis.opsForValue().increment(stockKey);
                throw new BusinessException(ErrorCode.FLASH_SALE_SOLD_OUT);
            }

            // 6. Atomic balance deduction (safe across multiple pods)
            int balanceUpdated = userRepo.deductBalance(userId, item.getFlashPrice());
            if (balanceUpdated == 0) {
                redis.opsForValue().increment(stockKey);
                throw new BusinessException(ErrorCode.FLASH_SALE_INSUFFICIENT_BALANCE);
            }

            // 8. Create order
            String orderNo = UUID.randomUUID().toString();
            FlashSaleOrder order = FlashSaleOrder.builder()
                    .orderNo(orderNo).userId(userId).flashSaleItemId(itemId)
                    .saleDate(today).amount(item.getFlashPrice()).status("SUCCESS").build();
            orderRepo.save(order);

            // 9. Publish Kafka event for inventory sync
            String event = String.format("{\"eventId\":\"%s\",\"type\":\"STOCK_DEDUCTED\",\"productId\":%d,\"quantity\":1}",
                    UUID.randomUUID(), item.getProductId());
            kafkaTemplate.send(KafkaConfig.TOPIC_INVENTORY_SYNC, String.valueOf(item.getProductId()), event);

            log.info("Purchase success: userId={}, itemId={}, orderNo={}", userId, itemId, orderNo);
            return orderNo;

        } catch (BusinessException e) {
            throw e; // re-throw business exceptions (Redis already rolled back)
        } catch (Exception e) {
            redis.opsForValue().increment(stockKey); // rollback Redis on unexpected error
            log.error("Purchase failed unexpectedly", e);
            throw e;
        }
    }
}
