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
}
