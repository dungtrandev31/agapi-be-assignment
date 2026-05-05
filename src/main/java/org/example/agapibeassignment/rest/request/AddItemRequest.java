package org.example.agapibeassignment.rest.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class AddItemRequest {
    @NotNull private Long productId;
    @NotNull @Positive private Integer saleQuantity;
    @NotNull @Positive private BigDecimal flashPrice;
}
