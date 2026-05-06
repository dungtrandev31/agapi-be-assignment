package org.com.application.service;

import org.com.application.entity.FlashSaleItem;
import org.com.application.entity.FlashSaleSlot;
import org.com.rest.request.AddItemRequest;
import org.com.rest.request.CreateSlotRequest;
import org.com.rest.request.UpdateItemRequest;
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
