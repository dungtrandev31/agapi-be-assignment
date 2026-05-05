package org.example.agapibeassignment.application.service;

import org.example.agapibeassignment.application.entity.FlashSaleItem;
import org.example.agapibeassignment.application.entity.FlashSaleSlot;
import org.example.agapibeassignment.rest.request.AddItemRequest;
import org.example.agapibeassignment.rest.request.CreateSlotRequest;
import org.example.agapibeassignment.rest.request.UpdateItemRequest;
import java.util.List;

public interface FlashSaleAdminService {
    FlashSaleSlot createSlot(CreateSlotRequest request);
    FlashSaleSlot updateSlot(Long slotId, CreateSlotRequest request);
    void deleteSlot(Long slotId);

    FlashSaleItem addItemToSlot(Long slotId, AddItemRequest request);
    FlashSaleItem updateItem(Long itemId, UpdateItemRequest request);
    void deleteItem(Long itemId);

    List<FlashSaleSlot> getAllSlots();
    List<FlashSaleItem> getItemsBySlot(Long slotId);
}
