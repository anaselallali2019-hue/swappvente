package com.shopstream.product.domain.model;

import com.shopstream.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregate Root: Product
 * 
 * F2.1: Création produit avec variantes.
 * 
 * PATTERN: Aggregate Root (DDD)
 * - Product = racine de l'aggregate
 * - ProductVariant = entité enfant (ne peut exister sans Product)
 * - Modification variantes = via Product uniquement
 * 
 * DÉCISIONS TECHNIQUES:
 * 
 * 1. @OneToMany cascade ALL:
 *    - Cascade persist: sauver produit sauve variantes automatiquement
 *    - Cascade remove: supprimer produit supprime variantes
 *    - orphanRemoval: supprimer variante de collection = DELETE DB
 * 
 * 2. @Embedded Price:
 *    - Value Object (DDD)
 *    - Pas d'identité propre
 *    - Immutable (final fields)
 * 
 * 3. @ElementCollection categories:
 *    - Collection simple (pas @OneToMany)
 *    - Table de jointure automatique
 *    - Bon pour données simples (tags, categories)
 * 
 * 4. @Formula rating_avg:
 *    - Calculé par Hibernate (SELECT AVG...)
 *    - Read-only (pas persisté)
 *    - Alternative: Materialized View ou cache
 */
@Entity
@Table(
    name = "products",
    indexes = {
        @Index(name = "idx_products_tenant", columnList = "tenant_id"),
        @Index(name = "idx_products_status", columnList = "status"),
        @Index(name = "idx_products_brand", columnList = "brand"),
        @Index(name = "idx_products_rating", columnList = "rating_avg"),
        @Index(name = "idx_products_views", columnList = "view_count")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE products SET deleted_at = NOW() WHERE id = ? AND version = ?")
@Where(clause = "deleted_at IS NULL")
public class Product extends BaseEntity {

    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @NotBlank
    @Column(name = "name", nullable = false, length = 500)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Prix de base (avant variantes).
     * 
     * POURQUOI @Embedded (Value Object):
     * - Prix = valeur + devise (2 champs liés)
     * - Pas d'identité propre
     * - Réutilisable (OrderItem, Invoice, etc.)
     * - Type-safe (pas de confusion amount/currency)
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "base_price", precision = 10, scale = 2)),
        @AttributeOverride(name = "currency", column = @Column(name = "currency", length = 3))
    })
    private Price basePrice;

    @Column(name = "brand", length = 255)
    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private ProductStatus status = ProductStatus.DRAFT;

    /**
     * Rating moyen calculé.
     * 
     * POURQUOI @Formula (vs column persisté):
     * ✅ Toujours à jour (pas de sync issues)
     * ✅ Pas de duplication données
     * ❌ Calculé à chaque SELECT (overhead léger)
     * 
     * ALTERNATIVE:
     * - Column persisté + trigger DB
     * - Materialized View
     * - Cache Redis
     */
    @Formula("(SELECT COALESCE(AVG(r.rating), 0) FROM product_reviews r WHERE r.product_id = id)")
    private BigDecimal ratingAvg;

    @Column(name = "rating_count")
    @Builder.Default
    private Integer ratingCount = 0;

    @Column(name = "view_count")
    @Builder.Default
    private Integer viewCount = 0;

    /**
     * Variantes du produit (taille, couleur, etc.).
     * 
     * POURQUOI cascade ALL + orphanRemoval:
     * - Variante ne peut exister sans produit (Aggregate boundary)
     * - Supprimer produit = supprimer variantes
     * - Retirer variante de collection = DELETE DB
     * 
     * FETCH LAZY:
     * - Défaut JPA (bonne pratique)
     * - Charger avec @EntityGraph si besoin
     */
    @OneToMany(
        mappedBy = "product",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @Builder.Default
    private Set<ProductVariant> variants = new HashSet<>();

    /**
     * Catégories produit (multi-valued).
     * 
     * POURQUOI @ElementCollection (vs @ManyToMany):
     * - Simple (pas besoin entité Category)
     * - Table jointure automatique
     * - Bon si catégories = simples IDs ou strings
     * 
     * QUAND NE PAS UTILISER:
     * - Si Category = entité complexe avec champs propres
     * - Si besoin lazy loading (ElementCollection = eager par défaut)
     */
    @ElementCollection
    @CollectionTable(
        name = "product_categories",
        joinColumns = @JoinColumn(name = "product_id")
    )
    @Column(name = "category_id")
    @Builder.Default
    private Set<UUID> categories = new HashSet<>();

    /**
     * Tags pour recherche (keywords).
     */
    @ElementCollection
    @CollectionTable(
        name = "product_tags",
        joinColumns = @JoinColumn(name = "product_id")
    )
    @Column(name = "tag")
    @Builder.Default
    private Set<String> tags = new HashSet<>();

    // ========== Business Logic Methods ==========

    /**
     * Ajouter une variante.
     * 
     * POURQUOI dans l'entité (vs service):
     * - Domain logic appartient au domain model
     * - Garantit invariants (bidirectional relationship)
     * - Évite état incohérent
     */
    public void addVariant(ProductVariant variant) {
        variants.add(variant);
        variant.setProduct(this);
    }

    public void removeVariant(ProductVariant variant) {
        variants.remove(variant);
        variant.setProduct(null);
    }

    /**
     * Publier produit (DRAFT → ACTIVE).
     */
    public void publish() {
        if (variants.isEmpty()) {
            throw new IllegalStateException(
                "Cannot publish product without variants. Add at least one variant."
            );
        }
        this.status = ProductStatus.ACTIVE;
    }

    public void archive() {
        this.status = ProductStatus.ARCHIVED;
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    public boolean isAvailableForSale() {
        return status == ProductStatus.ACTIVE;
    }
}
