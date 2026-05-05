package org.example.agapibeassignment.application.repository;

import org.example.agapibeassignment.application.entity.InventoryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;

public interface InventoryEventRepository extends JpaRepository<InventoryEvent, Long> {
    boolean existsByEventId(String eventId);
    boolean existsByEventIdAndStatus(String eventId, String status);

    @Modifying
    @Query("UPDATE InventoryEvent e SET e.status = :status, e.processedAt = :processedAt WHERE e.eventId = :eventId")
    int updateStatusByEventId(String eventId, String status, LocalDateTime processedAt);
}
