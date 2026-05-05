package org.example.agapibeassignment.application.repository;

import org.example.agapibeassignment.application.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Atomic stock deduction — single UPDATE, no read-modify-write race condition.
     * Uses GREATEST(0, ...) to prevent negative stock.
     * Returns 1 if product found and updated, 0 if product not found.
     */
    @Modifying
    @Query("UPDATE Product p SET p.totalStock = GREATEST(0, p.totalStock - :quantity) WHERE p.id = :productId")
    int deductStock(Long productId, int quantity);

    /**
     * Atomic stock restoration — single UPDATE.
     * Returns 1 if product found and updated, 0 if product not found.
     */
    @Modifying
    @Query("UPDATE Product p SET p.totalStock = p.totalStock + :quantity WHERE p.id = :productId")
    int restoreStock(Long productId, int quantity);
}
