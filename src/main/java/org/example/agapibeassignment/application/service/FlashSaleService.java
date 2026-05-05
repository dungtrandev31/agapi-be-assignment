package org.example.agapibeassignment.application.service;

import org.example.agapibeassignment.rest.response.FlashSaleItemResponse;
import java.util.List;

public interface FlashSaleService {
    List<FlashSaleItemResponse> getCurrentFlashSaleItems();
    String purchase(Long userId, Long itemId);
}
