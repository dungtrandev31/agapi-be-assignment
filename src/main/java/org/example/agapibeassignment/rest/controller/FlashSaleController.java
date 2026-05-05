package org.example.agapibeassignment.rest.controller;

import org.example.agapibeassignment.application.dto.ApiResponse;
import org.example.agapibeassignment.application.service.FlashSaleService;
import org.example.agapibeassignment.rest.response.FlashSaleItemResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/flash-sales")
public class FlashSaleController {
    private final FlashSaleService flashSaleService;

    public FlashSaleController(FlashSaleService flashSaleService) { this.flashSaleService = flashSaleService; }

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<List<FlashSaleItemResponse>>> getCurrent() {
        return ResponseEntity.ok(ApiResponse.ok(flashSaleService.getCurrentFlashSaleItems()));
    }

    @PostMapping("/{itemId}/purchase")
    public ResponseEntity<ApiResponse<Map<String, String>>> purchase(@PathVariable Long itemId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        String orderNo = flashSaleService.purchase(userId, itemId);
        return ResponseEntity.ok(ApiResponse.ok("Purchase successful", Map.of("orderNo", orderNo)));
    }
}
