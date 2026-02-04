package com.shopstream.tenant.application.dto;

import com.shopstream.tenant.domain.model.TenantStatus;
import com.shopstream.tenant.domain.model.TenantTier;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO pour réponse tenant.
 */
@Data
public class TenantResponse {
    private UUID id;
    private String name;
    private String subdomain;
    private String customDomain;
    private TenantTier tier;
    private TenantStatus status;
    private String settings;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
    
    // Champs calculés
    private Integer userCount;
    private Boolean hasActiveSubscription;
}
