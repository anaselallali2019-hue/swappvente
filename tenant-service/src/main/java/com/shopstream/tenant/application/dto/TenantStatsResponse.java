package com.shopstream.tenant.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * DTO pour statistiques dashboard admin.
 * 
 * POURQUOI Projection Interface vs DTO class:
 * 
 * PROJECTION INTERFACE (Spring Data JPA):
 * ```java
 * interface TenantStats {
 *     Long getTotalTenants();
 *     BigDecimal getTotalRevenue();
 * }
 * ```
 * ✅ Type-safe
 * ✅ Pas de mapping manuel
 * ❌ Limité aux champs query
 * 
 * DTO CLASS (ici):
 * ✅ Plus flexible (computed fields, nested objects)
 * ✅ Serialization control (Jackson annotations)
 * ❌ Mapping manuel nécessaire
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantStatsResponse {
    
    /**
     * Nombre total de tenants.
     */
    private Long totalTenants;
    
    /**
     * Répartition par tier: {FREE: 100, PRO: 50, ENTERPRISE: 10}
     */
    private Map<String, Long> tenantsByTier;
    
    /**
     * Répartition par status: {ACTIVE: 150, SUSPENDED: 10}
     */
    private Map<String, Long> tenantsByStatus;
    
    /**
     * Chiffre d'affaires total mensuel.
     */
    private BigDecimal totalMonthlyRevenue;
    
    /**
     * Croissance mensuelle (%).
     */
    private BigDecimal monthlyGrowthPercentage;
    
    /**
     * Tenants créés ce mois.
     */
    private Long newTenantsThisMonth;
    
    /**
     * Tenants actifs (au moins 1 commande dans 30 derniers jours).
     */
    private Long activeTenantsLast30Days;
}
