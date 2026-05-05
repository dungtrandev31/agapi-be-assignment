package org.example.agapibeassignment.application.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "flash_sale_orders")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class FlashSaleOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_no", nullable = false, unique = true)
    private String orderNo;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "flash_sale_item_id", nullable = false)
    private Long flashSaleItemId;
    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(nullable = false) @Builder.Default
    private String status = "SUCCESS";
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
