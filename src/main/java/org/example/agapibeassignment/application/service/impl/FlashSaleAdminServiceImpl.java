package org.example.agapibeassignment.application.service.impl;

import org.example.agapibeassignment.application.common.exception.BusinessException;
import org.example.agapibeassignment.application.common.exception.ErrorCode;
import org.example.agapibeassignment.application.entity.FlashSaleItem;
import org.example.agapibeassignment.application.entity.FlashSaleSlot;
import org.example.agapibeassignment.application.entity.Product;
import org.example.agapibeassignment.application.repository.FlashSaleItemRepository;
import org.example.agapibeassignment.application.repository.FlashSaleSlotRepository;
import org.example.agapibeassignment.application.repository.ProductRepository;
import org.example.agapibeassignment.application.service.FlashSaleAdminService;
import org.example.agapibeassignment.rest.request.AddItemRequest;
import org.example.agapibeassignment.rest.request.CreateSlotRequest;
import org.example.agapibeassignment.rest.request.UpdateItemRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FlashSaleAdminServiceImpl implements FlashSaleAdminService {
    private static final Logger log = LoggerFactory.getLogger(FlashSaleAdminServiceImpl.class);
    private static final String STOCK_KEY = "flash_sale_stock:";

    private final FlashSaleSlotRepository slotRepo;
    private final FlashSaleItemRepository itemRepo;
    private final ProductRepository productRepo;
    private final RedisTemplate<String, Object> redis;

    public FlashSaleAdminServiceImpl(FlashSaleSlotRepository slotRepo, FlashSaleItemRepository itemRepo,
            ProductRepository productRepo, RedisTemplate<String, Object> redis) {
        this.slotRepo = slotRepo;
        this.itemRepo = itemRepo;
        this.productRepo = productRepo;
        this.redis = redis;
    }

    // ── Slot CRUD ──

    @Override
    @Transactional
    public FlashSaleSlot createSlot(CreateSlotRequest req) {
        // Validate: end time must be after start time
        if (!req.getEndTime().isAfter(req.getStartTime())) {
            throw new BusinessException(ErrorCode.FLASH_SALE_INVALID_SLOT_TIME);
        }

        // Validate: no overlapping slot on the same date
        if (slotRepo.existsOverlappingSlot(req.getSaleDate(), req.getStartTime(), req.getEndTime())) {
            throw new BusinessException(ErrorCode.FLASH_SALE_SLOT_OVERLAP,
                    String.format("Slot overlaps on %s between %s-%s",
                            req.getSaleDate(), req.getStartTime(), req.getEndTime()));
        }

        FlashSaleSlot slot = FlashSaleSlot.builder()
                .saleDate(req.getSaleDate()).startTime(req.getStartTime()).endTime(req.getEndTime()).build();
        slot = slotRepo.save(slot);
        log.info("Slot created: id={}, date={}, {}-{}", slot.getId(), slot.getSaleDate(), slot.getStartTime(),
                slot.getEndTime());
        return slot;
    }

    @Override
    @Transactional
    public FlashSaleSlot updateSlot(Long slotId, CreateSlotRequest req) {
        FlashSaleSlot slot = slotRepo.findById(slotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FLASH_SALE_SLOT_NOT_FOUND));

        // Validate: end time must be after start time
        if (!req.getEndTime().isAfter(req.getStartTime())) {
            throw new BusinessException(ErrorCode.FLASH_SALE_INVALID_SLOT_TIME);
        }

        // Validate: no overlapping slot (excluding this one)
        if (slotRepo.existsOverlappingSlotExcluding(req.getSaleDate(), req.getStartTime(), req.getEndTime(), slotId)) {
            throw new BusinessException(ErrorCode.FLASH_SALE_SLOT_OVERLAP,
                    String.format("Slot overlaps on %s between %s-%s",
                            req.getSaleDate(), req.getStartTime(), req.getEndTime()));
        }

        slot.setSaleDate(req.getSaleDate());
        slot.setStartTime(req.getStartTime());
        slot.setEndTime(req.getEndTime());
        slot = slotRepo.save(slot);
        log.info("Slot updated: id={}", slotId);
        return slot;
    }

    @Override
    @Transactional
    public void deleteSlot(Long slotId) {
        FlashSaleSlot slot = slotRepo.findById(slotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FLASH_SALE_SLOT_NOT_FOUND));
        // Delete all items in this slot and their Redis stock keys
        List<FlashSaleItem> items = itemRepo.findBySlotId(slotId);
        for (FlashSaleItem item : items) {
            redis.delete(STOCK_KEY + item.getId());
        }
        itemRepo.deleteAll(items);
        slotRepo.delete(slot);
        log.info("Slot deleted: id={}, items removed={}", slotId, items.size());
    }

    @Override
    @Transactional
    public FlashSaleItem addItemToSlot(Long slotId, AddItemRequest req) {
        slotRepo.findById(slotId).orElseThrow(() -> new BusinessException(ErrorCode.FLASH_SALE_SLOT_NOT_FOUND));
        Product product = productRepo.findById(req.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Product not found: " + req.getProductId()));

        // Validate: product not already in this slot
        if (itemRepo.existsBySlotIdAndProductId(slotId, req.getProductId())) {
            throw new BusinessException(ErrorCode.FLASH_SALE_DUPLICATE_PRODUCT,
                    String.format("Product %d already exists in slot %d", req.getProductId(), slotId));
        }

        // Validate: total committed sale qty across ALL slots must not exceed warehouse
        // stock
        int alreadyCommitted = itemRepo.sumSaleQuantityByProductId(req.getProductId());
        int availableForSale = product.getTotalStock() - alreadyCommitted;
        if (req.getSaleQuantity() > availableForSale) {
            throw new BusinessException(ErrorCode.FLASH_SALE_EXCEEDS_STOCK,
                    String.format("Requested %d but only %d available (warehouse: %d, already committed: %d)",
                            req.getSaleQuantity(), Math.max(0, availableForSale),
                            product.getTotalStock(), alreadyCommitted));
        }

        FlashSaleItem item = FlashSaleItem.builder()
                .slotId(slotId).productId(req.getProductId())
                .saleQuantity(req.getSaleQuantity()).flashPrice(req.getFlashPrice()).build();
        item = itemRepo.save(item);

        redis.opsForValue().set(STOCK_KEY + item.getId(), item.getSaleQuantity());
        log.info("Item added: id={}, slotId={}, productId={}, qty={}, availableStock={}",
                item.getId(), slotId, req.getProductId(), req.getSaleQuantity(), availableForSale);
        return item;
    }

    @Override
    @Transactional
    public FlashSaleItem updateItem(Long itemId, UpdateItemRequest req) {
        FlashSaleItem item = itemRepo.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FLASH_SALE_ITEM_NOT_FOUND));

        // Validate: total committed qty (excluding this item) + new qty must not exceed
        // warehouse stock
        Product product = productRepo.findById(item.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Product not found: " + item.getProductId()));
        int otherCommitted = itemRepo.sumSaleQuantityByProductIdExcluding(item.getProductId(), itemId);
        int availableForSale = product.getTotalStock() - otherCommitted;
        if (req.getSaleQuantity() > availableForSale) {
            throw new BusinessException(ErrorCode.FLASH_SALE_EXCEEDS_STOCK,
                    String.format("Requested %d but only %d available (warehouse: %d, other committed: %d)",
                            req.getSaleQuantity(), Math.max(0, availableForSale),
                            product.getTotalStock(), otherCommitted));
        }

        item.setSaleQuantity(req.getSaleQuantity());
        item.setFlashPrice(req.getFlashPrice());
        FlashSaleItem savedItem = itemRepo.save(item);

        // Update Redis stock (remaining = new sale qty - sold qty)
        int remaining = savedItem.getSaleQuantity() - savedItem.getSoldQuantity();
        redis.opsForValue().set(STOCK_KEY + savedItem.getId(), Math.max(0, remaining));
        log.info("Item updated: id={}, newQty={}, newPrice={}, availableStock={}",
                itemId, req.getSaleQuantity(), req.getFlashPrice(), availableForSale);
        return savedItem;
    }

    @Override
    @Transactional
    public void deleteItem(Long itemId) {
        FlashSaleItem item = itemRepo.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FLASH_SALE_ITEM_NOT_FOUND));
        redis.delete(STOCK_KEY + item.getId());
        itemRepo.delete(item);
        log.info("Item deleted: id={}", itemId);
    }

    // ── Queries ──

    @Override
    public List<FlashSaleSlot> getAllSlots() {
        return slotRepo.findAll();
    }

    @Override
    public List<FlashSaleItem> getItemsBySlot(Long slotId) {
        slotRepo.findById(slotId).orElseThrow(() -> new BusinessException(ErrorCode.FLASH_SALE_SLOT_NOT_FOUND));
        return itemRepo.findBySlotId(slotId);
    }
}
