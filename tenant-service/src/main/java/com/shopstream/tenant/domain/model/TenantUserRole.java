package com.shopstream.tenant.domain.model;

/**
 * Rôles des users au niveau tenant.
 */
public enum TenantUserRole {
    /**
     * Admin du tenant, peut tout gérer.
     */
    TENANT_ADMIN,

    /**
     * Vendeur, peut gérer produits et commandes.
     */
    SELLER,

    /**
     * Support, lecture seule.
     */
    SUPPORT
}
