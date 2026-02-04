package com.shopstream.tenant.domain.model;

/**
 * Status d'un tenant.
 */
public enum TenantStatus {
    /**
     * Tenant actif, peut utiliser la plateforme.
     */
    ACTIVE,

    /**
     * Suspendu (non-paiement, violation ToS).
     */
    SUSPENDED,

    /**
     * En cours de suppression (soft delete en cours).
     */
    DELETED,

    /**
     * Trial expiré, en attente d'upgrade.
     */
    TRIAL_EXPIRED,

    /**
     * Onboarding non terminé.
     */
    PENDING_SETUP
}
