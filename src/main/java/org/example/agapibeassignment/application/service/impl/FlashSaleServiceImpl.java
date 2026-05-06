package org.example.agapibeassignment.application.service.impl;

import org.example.agapibeassignment.application.common.exception.BusinessException;
import org.example.agapibeassignment.application.common.exception.ErrorCode;
import org.example.agapibeassignment.application.entity.*;
import org.example.agapibeassignment.application.inventory.InventoryDeductionEvent;
import org.example.agapibeassignment.application.repository.*;
import org.example.agapibeassignment.application.service.FlashSaleService;
import org.example.agapibeassignment.rest.response.FlashSaleItemResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
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
    private final ApplicationEventPublisher eventPublisher;

    public FlashSaleServiceImpl(FlashSaleSlotRepository slotRepo, FlashSaleItemRepository itemRepo,
                                FlashSaleOrderRepository orderRepo, ProductRepository productRepo,
                                UserRepository userRepo, RedisTemplate<String, Object> redis,
                                ApplicationEventPublisher eventPublisher) {
        this.slotRepo = slotRepo;
        this.itemRepo = itemRepo;
        this.orderRepo = orderRepo;
        this.productRepo = productRepo;
        this.userRepo = userRepo;
        this.redis = redis;
        this.eventPublisher = eventPublisher;
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

    /**
     * Purchase flow — Saga pattern with eventual consistency:
     *
     * SYNC (fast, returns immediately):
     *   1. Redis DECR        — fast gate
     *   2. DB sold_quantity   — atomic increment
     *   3. DB balance         — atomic deduction
     *   4. DB order           — status = PENDING
     *   5. Spring Event       → Kafka AFTER_COMMIT
     *
     * ASYNC (Kafka consumer):
     *   6a. SUCCESS → deduct warehouse stock → order = SUCCESS
     *   6b. FAILURE → compensate: restore balance, sold_qty, Redis, order = CANCELLED
     */
    @Override
    @Transactional
    public String purchase(Long userId, Long itemId) {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        // 1. Check: user already purchased today? (compensated orders are deleted, so simple check works)
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
            redis.opsForValue().increment(stockKey);
            throw new BusinessException(ErrorCode.FLASH_SALE_SOLD_OUT);
        }

        try {
            // 5. Atomic DB: increment sold_quantity
            int updated = itemRepo.incrementSoldQuantity(itemId);
            if (updated == 0) {
                redis.opsForValue().increment(stockKey);
                throw new BusinessException(ErrorCode.FLASH_SALE_SOLD_OUT);
            }

            // 6. Atomic DB: deduct user balance
            int balanceUpdated = userRepo.deductBalance(userId, item.getFlashPrice());
            if (balanceUpdated == 0) {
                redis.opsForValue().increment(stockKey);
                throw new BusinessException(ErrorCode.FLASH_SALE_INSUFFICIENT_BALANCE);
            }

            // 7. Create order with PENDING status (confirmed async by Kafka consumer)
            String orderNo = UUID.randomUUID().toString();
            FlashSaleOrder order = FlashSaleOrder.builder()
                    .orderNo(orderNo).userId(userId).flashSaleItemId(itemId)
                    .saleDate(today).amount(item.getFlashPrice()).status("PENDING").build();
            orderRepo.save(order);

            // 8. Publish event → Kafka message sent AFTER_COMMIT with full saga context
            eventPublisher.publishEvent(new InventoryDeductionEvent(
                    item.getProductId(), 1,
                    itemId, userId, item.getFlashPrice(), orderNo));

            log.info("Purchase initiated: userId={}, itemId={}, orderNo={}, status=PENDING",
                    userId, itemId, orderNo);
            return orderNo;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            redis.opsForValue().increment(stockKey);
            log.error("Purchase failed unexpectedly", e);
            throw e;
        }
    }
}
