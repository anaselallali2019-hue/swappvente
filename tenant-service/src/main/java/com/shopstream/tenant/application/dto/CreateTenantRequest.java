package com.shopstream.tenant.application.dto;

import com.shopstream.tenant.domain.model.TenantTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * DTO pour création d'un tenant.
 * 
 * POURQUOI DTO (et pas entité directement):
 * ✅ Séparation API contract vs domain model
 * ✅ Validation spécifique à l'opération
 * ✅ Évite over-posting (client envoie champs non autorisés)
 * ✅ Évolution API indépendante du modèle DB
 * 
 * PATTERN: Data Transfer Object (DTO)
 */
@Data
public class CreateTenantRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Subdomain is required")
    @Pattern(
        regexp = "^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$",
        message = "Subdomain must be 3-30 lowercase alphanumeric characters or hyphens"
    )
    private String subdomain;

    private TenantTier tier = TenantTier.FREE;

    private String adminEmail;

    private String settings; // JSON string
}
