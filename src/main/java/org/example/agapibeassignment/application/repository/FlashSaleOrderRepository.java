package org.example.agapibeassignment.application.repository;

import org.example.agapibeassignment.application.entity.FlashSaleOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;

public interface FlashSaleOrderRepository extends JpaRepository<FlashSaleOrder, Long> {
    boolean existsByUserIdAndSaleDate(Long userId, LocalDate saleDate);

    /**
     * Atomic status update for saga: PENDING → SUCCESS.
     */
    @Modifying
    @Query("UPDATE FlashSaleOrder o SET o.status = :newStatus WHERE o.orderNo = :orderNo")
    int updateStatusByOrderNo(String orderNo, String newStatus);

    /**
     * Delete order by orderNo — used in saga compensation.
     * Frees the UNIQUE(user_id, sale_date) constraint so user can retry.
     */
    @Modifying
    @Query("DELETE FROM FlashSaleOrder o WHERE o.orderNo = :orderNo")
    int deleteByOrderNo(String orderNo);
}
