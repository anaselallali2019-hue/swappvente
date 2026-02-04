package com.shopstream.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception levée quand une ressource n'est pas trouvée.
 * 
 * UTILISATION:
 * throw new ResourceNotFoundException("Tenant", tenantId);
 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resourceType, Object resourceId) {
        super(
            String.format("%s with id '%s' not found", resourceType, resourceId),
            "RESOURCE_NOT_FOUND",
            HttpStatus.NOT_FOUND.value()
        );
    }
}
