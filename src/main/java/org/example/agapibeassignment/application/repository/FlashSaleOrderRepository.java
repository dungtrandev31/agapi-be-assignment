package org.example.agapibeassignment.application.repository;

import org.example.agapibeassignment.application.entity.FlashSaleOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;

public interface FlashSaleOrderRepository extends JpaRepository<FlashSaleOrder, Long> {
    boolean existsByUserIdAndSaleDate(Long userId, LocalDate saleDate);
}
