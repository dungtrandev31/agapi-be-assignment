package org.example.agapibeassignment.rest.response;

import lombok.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FlashSaleItemResponse {
    private Long itemId;
    private Long productId;
    private String productName;
    private String productDescription;
    private BigDecimal originalPrice;
    private BigDecimal flashPrice;
    private int availableQuantity;
    private String slotTime;
}
