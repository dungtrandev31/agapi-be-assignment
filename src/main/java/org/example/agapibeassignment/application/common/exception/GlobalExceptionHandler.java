package org.example.agapibeassignment.application.common.exception;

import org.example.agapibeassignment.application.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode().getCode()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(mapStatus(ex.getErrorCode()))
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode().getCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid", (a, b) -> a));
        return ResponseEntity.badRequest().body(ApiResponse.<Map<String, String>>builder()
                .success(false).message("Validation failed").errorCode(ErrorCode.VALIDATION_FAILED.getCode()).data(errors).build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred", ErrorCode.INTERNAL_ERROR.getCode()));
    }

    private HttpStatus mapStatus(ErrorCode ec) {
        return switch (ec) {
            case AUTH_INVALID_CREDENTIALS, AUTH_TOKEN_EXPIRED, AUTH_TOKEN_INVALID -> HttpStatus.UNAUTHORIZED;
            case AUTH_ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case AUTH_USER_NOT_FOUND, FLASH_SALE_ITEM_NOT_FOUND, FLASH_SALE_SLOT_NOT_FOUND, RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case AUTH_USER_ALREADY_EXISTS, FLASH_SALE_ALREADY_PURCHASED, FLASH_SALE_SOLD_OUT -> HttpStatus.CONFLICT;
            case FLASH_SALE_INSUFFICIENT_BALANCE -> HttpStatus.PAYMENT_REQUIRED;
            case AUTH_INVALID_OTP, AUTH_OTP_NOT_VERIFIED, AUTH_INVALID_IDENTIFIER, VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
