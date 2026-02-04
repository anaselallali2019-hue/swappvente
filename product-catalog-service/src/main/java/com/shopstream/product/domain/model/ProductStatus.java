package com.shopstream.product.domain.model;

/**
 * Status d'un produit.
 */
public enum ProductStatus {
    /**
     * Brouillon, pas encore publié.
     */
    DRAFT,

    /**
     * Publié et visible pour clients.
     */
    ACTIVE,

    /**
     * Archivé (plus vendu mais gardé pour historique).
     */
    ARCHIVED,

    /**
     * Out of stock temporairement.
     */
    OUT_OF_STOCK
}
