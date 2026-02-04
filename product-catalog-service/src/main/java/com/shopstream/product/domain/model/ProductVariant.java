package com.shopstream.product.domain.model;

import com.shopstream.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Product Variant (taille, couleur, etc.).
 * 
 * PATTERN: Entity (part of Aggregate)
 * - Ne peut exister sans Product (parent)
 * - Accès via Product uniquement
 * 
 * EXEMPLE:
 * Product: T-shirt "Classic"
 * Variants:
 *   - SKU: TSHIRT-RED-S, size=S, color=Red, price=19.99
 *   - SKU: TSHIRT-RED-M, size=M, color=Red, price=19.99
 *   - SKU: TSHIRT-BLUE-S, size=S, color=Blue, price=19.99
 *   ...
 */
@Entity
@Table(
    name = "product_variants",
    indexes = {
        @Index(name = "idx_variants_product", columnList = "product_id"),
        @Index(name = "idx_variants_sku", columnList = "sku", unique = true)
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_sku", columnNames = "sku")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @NotNull
    private Product product;

    /**
     * SKU (Stock Keeping Unit) unique.
     * 
     * POURQUOI unique globalement:
     * - Identifier variante de manière unique
     * - Éviter doublons entre tenants (si multi-marketplace)
     * - Traçabilité logistique
     */
    @NotBlank
    @Column(name = "sku", nullable = false, unique = true, length = 100)
    private String sku;

    @Column(name = "size", length = 50)
    private String size;

    @Column(name = "color", length = 50)
    private String color;

    /**
     * Prix spécifique de la variante (override base price si set).
     */
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "price", precision = 10, scale = 2)),
        @AttributeOverride(name = "currency", column = @Column(name = "currency", length = 3))
    })
    private Price price;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /**
     * Barcode pour scan physique.
     */
    @Column(name = "barcode", length = 100)
    private String barcode;

    /**
     * Poids (pour calcul shipping).
     */
    @Column(name = "weight_grams")
    private Integer weightGrams;

    /**
     * Get effective price (variant price ou product base price).
     */
    public Price getEffectivePrice() {
        return price != null ? price : product.getBasePrice();
    }
}
