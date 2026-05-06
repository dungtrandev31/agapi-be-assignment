package org.example.agapibeassignment.application.repository;

import org.example.agapibeassignment.application.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);

    /**
     * Atomic balance deduction – prevents race condition across multiple pods.
     * Returns 1 if successful, 0 if insufficient balance.
     */
    @Modifying
    @Query("UPDATE User u SET u.balance = u.balance - :amount WHERE u.id = :userId AND u.balance >= :amount")
    int deductBalance(Long userId, BigDecimal amount);

    /**
     * Atomic balance restoration — compensating transaction for saga rollback.
     */
    @Modifying
    @Query("UPDATE User u SET u.balance = u.balance + :amount WHERE u.id = :userId")
    int restoreBalance(Long userId, BigDecimal amount);
}

