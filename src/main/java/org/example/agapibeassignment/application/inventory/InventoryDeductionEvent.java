package org.example.agapibeassignment.application.inventory;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Spring application event carrying all data needed for:
 *  1. Warehouse stock deduction (productId, quantity)
 *  2. Saga compensation on failure (itemId, userId, amount, orderNo)
 *
 * Published during purchase → Kafka message sent AFTER_COMMIT.
 */
@Getter
@AllArgsConstructor
public class InventoryDeductionEvent {
    private final Long productId;
    private final int quantity;
    private final Long itemId;       // for sold_quantity rollback
    private final Long userId;       // for balance restoration
    private final BigDecimal amount; // for balance restoration
    private final String orderNo;    // for order status update
}
