package org.example.agapibeassignment.application.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "flash_sale_items")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class FlashSaleItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "slot_id", nullable = false)
    private Long slotId;
    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(name = "sale_quantity", nullable = false)
    private int saleQuantity;
    @Column(name = "sold_quantity", nullable = false) @Builder.Default
    private int soldQuantity = 0;
    @Column(name = "flash_price", nullable = false)
    private BigDecimal flashPrice;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
