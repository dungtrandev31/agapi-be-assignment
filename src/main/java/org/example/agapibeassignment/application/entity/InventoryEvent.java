package org.example.agapibeassignment.application.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_events")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class InventoryEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "quantity_change", nullable = false)
    private int quantityChange;

    @Column(nullable = false) @Builder.Default
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
