package org.example.agapibeassignment.application.repository;

import org.example.agapibeassignment.application.entity.FlashSaleSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface FlashSaleSlotRepository extends JpaRepository<FlashSaleSlot, Long> {

    @Query("SELECT s FROM FlashSaleSlot s WHERE s.saleDate = :date AND s.startTime <= :time AND s.endTime > :time")
    List<FlashSaleSlot> findActiveSlots(LocalDate date, LocalTime time);

    /**
     * Check for overlapping slots on the same date.
     * Overlap condition: startA < endB AND startB < endA
     */
    @Query("SELECT COUNT(s) > 0 FROM FlashSaleSlot s WHERE s.saleDate = :date AND s.startTime < :endTime AND s.endTime > :startTime")
    boolean existsOverlappingSlot(LocalDate date, LocalTime startTime, LocalTime endTime);

    /**
     * Check for overlapping slots excluding a specific slot (for update).
     */
    @Query("SELECT COUNT(s) > 0 FROM FlashSaleSlot s WHERE s.saleDate = :date AND s.startTime < :endTime AND s.endTime > :startTime AND s.id <> :excludeId")
    boolean existsOverlappingSlotExcluding(LocalDate date, LocalTime startTime, LocalTime endTime, Long excludeId);
}
