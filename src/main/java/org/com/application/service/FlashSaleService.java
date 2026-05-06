package org.com.application.service;

import org.com.rest.response.FlashSaleItemResponse;
import java.util.List;

public interface FlashSaleService {
    List<FlashSaleItemResponse> getCurrentFlashSaleItems();
    String purchase(Long userId, Long itemId);
}
