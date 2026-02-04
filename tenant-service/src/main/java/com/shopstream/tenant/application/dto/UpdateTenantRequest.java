package com.shopstream.tenant.application.dto;

import com.shopstream.tenant.domain.model.TenantTier;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * DTO pour mise à jour d'un tenant.
 * 
 * NOTE: Tous les champs optionnels (partial update).
 */
@Data
public class UpdateTenantRequest {

    private String name;

    @Pattern(
        regexp = "^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$",
        message = "Subdomain must be 3-30 lowercase alphanumeric characters or hyphens"
    )
    private String subdomain;

    private String customDomain;

    private TenantTier tier;

    private String settings;
}
