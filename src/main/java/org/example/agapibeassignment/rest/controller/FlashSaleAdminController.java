package org.example.agapibeassignment.rest.controller;

import jakarta.validation.Valid;
import org.example.agapibeassignment.application.dto.ApiResponse;
import org.example.agapibeassignment.application.entity.FlashSaleItem;
import org.example.agapibeassignment.application.entity.FlashSaleSlot;
import org.example.agapibeassignment.application.service.FlashSaleAdminService;
import org.example.agapibeassignment.rest.request.AddItemRequest;
import org.example.agapibeassignment.rest.request.CreateSlotRequest;
import org.example.agapibeassignment.rest.request.UpdateItemRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/flash-sales")
public class FlashSaleAdminController {
    private final FlashSaleAdminService adminService;

    public FlashSaleAdminController(FlashSaleAdminService adminService) { this.adminService = adminService; }

    // ── Slots ──

    @PostMapping("/slots")
    public ResponseEntity<ApiResponse<FlashSaleSlot>> createSlot(@Valid @RequestBody CreateSlotRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Slot created", adminService.createSlot(req)));
    }

    @PutMapping("/slots/{slotId}")
    public ResponseEntity<ApiResponse<FlashSaleSlot>> updateSlot(@PathVariable Long slotId,
                                                                  @Valid @RequestBody CreateSlotRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Slot updated", adminService.updateSlot(slotId, req)));
    }

    @DeleteMapping("/slots/{slotId}")
    public ResponseEntity<ApiResponse<Void>> deleteSlot(@PathVariable Long slotId) {
        adminService.deleteSlot(slotId);
        return ResponseEntity.ok(ApiResponse.ok("Slot deleted", null));
    }

    @GetMapping("/slots")
    public ResponseEntity<ApiResponse<List<FlashSaleSlot>>> getSlots() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getAllSlots()));
    }

    // ── Items ──

    @PostMapping("/slots/{slotId}/items")
    public ResponseEntity<ApiResponse<FlashSaleItem>> addItem(@PathVariable Long slotId,
                                                               @Valid @RequestBody AddItemRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Item added", adminService.addItemToSlot(slotId, req)));
    }

    @GetMapping("/slots/{slotId}/items")
    public ResponseEntity<ApiResponse<List<FlashSaleItem>>> getItems(@PathVariable Long slotId) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getItemsBySlot(slotId)));
    }

    @PutMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<FlashSaleItem>> updateItem(@PathVariable Long itemId,
                                                                  @Valid @RequestBody UpdateItemRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Item updated", adminService.updateItem(itemId, req)));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<Void>> deleteItem(@PathVariable Long itemId) {
        adminService.deleteItem(itemId);
        return ResponseEntity.ok(ApiResponse.ok("Item deleted", null));
    }
}
