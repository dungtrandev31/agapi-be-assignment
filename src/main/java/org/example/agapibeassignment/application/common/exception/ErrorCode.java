package org.example.agapibeassignment.application.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    AUTH_INVALID_CREDENTIALS("AUTH_001", "Invalid credentials"),
    AUTH_USER_ALREADY_EXISTS("AUTH_002", "User already exists"),
    AUTH_USER_NOT_FOUND("AUTH_003", "User not found"),
    AUTH_INVALID_OTP("AUTH_004", "Invalid or expired OTP"),
    AUTH_OTP_NOT_VERIFIED("AUTH_005", "OTP not verified"),
    AUTH_TOKEN_EXPIRED("AUTH_006", "Token has expired"),
    AUTH_TOKEN_INVALID("AUTH_007", "Invalid token"),
    AUTH_INVALID_IDENTIFIER("AUTH_008", "Identifier must be a valid email or phone number"),
    AUTH_ACCESS_DENIED("AUTH_009", "Access denied"),
    FLASH_SALE_NOT_ACTIVE("FS_001", "Flash sale is not active at this time"),
    FLASH_SALE_SOLD_OUT("FS_002", "Flash sale item is sold out"),
    FLASH_SALE_ALREADY_PURCHASED("FS_003", "You have already purchased a flash sale item today"),
    FLASH_SALE_ITEM_NOT_FOUND("FS_004", "Flash sale item not found"),
    FLASH_SALE_SLOT_NOT_FOUND("FS_005", "Flash sale slot not found"),
    FLASH_SALE_INSUFFICIENT_BALANCE("FS_006", "Insufficient balance"),
    FLASH_SALE_EXCEEDS_STOCK("FS_007", "Sale quantity exceeds available warehouse stock"),
    FLASH_SALE_WAREHOUSE_OUT_OF_STOCK("FS_008", "Product is out of stock in warehouse"),
    FLASH_SALE_SLOT_OVERLAP("FS_009", "Time slot overlaps with an existing slot"),
    FLASH_SALE_DUPLICATE_PRODUCT("FS_010", "Product already exists in this slot"),
    FLASH_SALE_INVALID_SLOT_TIME("FS_011", "End time must be after start time"),
    INVENTORY_SYNC_FAILED("INV_001", "Inventory sync failed"),
    INVENTORY_DUPLICATE_EVENT("INV_002", "Duplicate inventory event"),
    RESOURCE_NOT_FOUND("GEN_001", "Resource not found"),
    VALIDATION_FAILED("GEN_002", "Validation failed"),
    INTERNAL_ERROR("GEN_003", "Internal server error");

    private final String code;
    private final String defaultMessage;
    ErrorCode(String code, String defaultMessage) { this.code = code; this.defaultMessage = defaultMessage; }
}
