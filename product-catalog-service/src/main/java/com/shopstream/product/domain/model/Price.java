package com.shopstream.product.domain.model;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Value Object: Price.
 * 
 * PATTERN: Value Object (DDD)
 * 
 * CARACTÉRISTIQUES:
 * - Pas d'identité propre (défini par ses attributs)
 * - Immutable (si possible, ici @Data pour JPA)
 * - Comparable par valeur (equals/hashCode sur tous champs)
 * - Réutilisable (Product, OrderItem, Invoice, etc.)
 * 
 * POURQUOI Value Object (vs 2 champs séparés):
 * ✅ Type-safe: impossible de confondre amount et currency
 * ✅ Cohésion: amount + currency toujours ensemble
 * ✅ Business logic centralisée (add, multiply, compare)
 * ✅ Validation unique (amount > 0, currency valid)
 * 
 * @Embeddable:
 * - Pas de table séparée
 * - Colonnes dans table parent (products, order_items, etc.)
 * - @AttributeOverride pour renommer colonnes si besoin
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Price {

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal amount;

    /**
     * Currency code ISO 4217 (EUR, USD, GBP).
     */
    @NotNull
    private String currency;

    /**
     * Add two prices (must be same currency).
     */
    public Price add(Price other) {
        validateSameCurrency(other);
        return new Price(
            this.amount.add(other.amount),
            this.currency
        );
    }

    /**
     * Multiply price by quantity.
     */
    public Price multiply(int quantity) {
        return new Price(
            this.amount.multiply(BigDecimal.valueOf(quantity)),
            this.currency
        );
    }

    /**
     * Apply discount percentage (0-100).
     */
    public Price applyDiscount(BigDecimal discountPercentage) {
        BigDecimal multiplier = BigDecimal.ONE.subtract(
            discountPercentage.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
        );
        return new Price(
            this.amount.multiply(multiplier).setScale(2, RoundingMode.HALF_UP),
            this.currency
        );
    }

    /**
     * Check if this price > other.
     */
    public boolean isGreaterThan(Price other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    private void validateSameCurrency(Price other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                "Cannot operate on prices with different currencies: " + 
                this.currency + " vs " + other.currency
            );
        }
    }

    /**
     * Factory method pour création simple.
     */
    public static Price of(BigDecimal amount, String currency) {
        return new Price(amount, currency);
    }

    public static Price zero(String currency) {
        return new Price(BigDecimal.ZERO, currency);
    }
}
