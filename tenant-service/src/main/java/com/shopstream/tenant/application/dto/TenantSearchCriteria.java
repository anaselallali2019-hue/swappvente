package com.shopstream.tenant.application.dto;

import com.shopstream.tenant.domain.model.TenantStatus;
import com.shopstream.tenant.domain.model.TenantTier;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Critères de recherche pour filtrage dynamique.
 * 
 * UTILISATION avec Specifications:
 * - Chaque champ non-null = un filtre appliqué
 * - Composition dynamique selon champs présents
 */
@Data
public class TenantSearchCriteria {
    private String name;
    private String subdomain;
    private TenantStatus status;
    private TenantTier tier;
    private LocalDateTime createdAfter;
    private LocalDateTime createdBefore;
    private Integer minUsers;
}
