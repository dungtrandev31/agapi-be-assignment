package org.example.agapibeassignment.application.repository;

import org.example.agapibeassignment.application.entity.FlashSaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface FlashSaleItemRepository extends JpaRepository<FlashSaleItem, Long> {

    List<FlashSaleItem> findBySlotIdIn(List<Long> slotIds);

    List<FlashSaleItem> findBySlotId(Long slotId);

    @Modifying
    @Query("UPDATE FlashSaleItem i SET i.soldQuantity = i.soldQuantity + 1 WHERE i.id = :id AND i.soldQuantity < i.saleQuantity")
    int incrementSoldQuantity(Long id);

    /**
     * Sum total saleQuantity for a product across all flash sale items,
     * excluding a specific item (useful during update to avoid counting self).
     * Returns 0 if no other items exist.
     */
    @Query("SELECT COALESCE(SUM(i.saleQuantity), 0) FROM FlashSaleItem i WHERE i.productId = :productId AND i.id <> :excludeItemId")
    int sumSaleQuantityByProductIdExcluding(Long productId, Long excludeItemId);

    /**
     * Sum total saleQuantity for a product across all flash sale items.
     */
    @Query("SELECT COALESCE(SUM(i.saleQuantity), 0) FROM FlashSaleItem i WHERE i.productId = :productId")
    int sumSaleQuantityByProductId(Long productId);
}
