package com.shopstream.tenant.domain.model;

import lombok.Getter;

/**
 * Plans tarifaires avec limites associées.
 * 
 * PATTERN: Strategy Pattern
 * - Chaque tier a comportements différents (max products, features, etc.)
 * - Facilement extensible (nouveau tier = nouvelle enum value)
 * 
 * ALTERNATIVE si logique complexe:
 * - Interface TenantTierStrategy avec implémentations FreeTierStrategy, ProTierStrategy
 * - Table tenant_tiers en DB avec configuration
 */
@Getter
public enum TenantTier {
    FREE(
        100,          // max_products
        1000,         // max_orders_per_month
        1,            // max_users
        false         // custom_domain
    ),
    PRO(
        10_000,
        50_000,
        10,
        true
    ),
    ENTERPRISE(
        Integer.MAX_VALUE,
        Integer.MAX_VALUE,
        100,
        true
    );

    private final int maxProducts;
    private final int maxOrdersPerMonth;
    private final int maxUsers;
    private final boolean customDomainAllowed;

    TenantTier(int maxProducts, int maxOrdersPerMonth, int maxUsers, boolean customDomainAllowed) {
        this.maxProducts = maxProducts;
        this.maxOrdersPerMonth = maxOrdersPerMonth;
        this.maxUsers = maxUsers;
        this.customDomainAllowed = customDomainAllowed;
    }

    public boolean canAddProducts(int currentCount) {
        return currentCount < maxProducts;
    }

    public boolean canProcessOrders(int monthlyCount) {
        return monthlyCount < maxOrdersPerMonth;
    }
}
