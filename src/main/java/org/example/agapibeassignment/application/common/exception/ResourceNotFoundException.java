package org.example.agapibeassignment.application.common.exception;

public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String resourceName, Object id) {
        super(ErrorCode.RESOURCE_NOT_FOUND, String.format("%s not found with id: %s", resourceName, id));
    }
}
