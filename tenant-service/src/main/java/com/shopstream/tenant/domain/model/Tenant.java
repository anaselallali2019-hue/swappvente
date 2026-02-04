package com.shopstream.tenant.domain.model;

import com.shopstream.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;
import org.hibernate.envers.Audited;

import java.util.HashSet;
import java.util.Set;

/**
 * Aggregate Root: Tenant
 * 
 * DÉCISIONS DE DESIGN ET POURQUOI:
 * 
 * 1. UUID pour PK (pas auto-increment):
 *    ✅ Sharding futur: pas de collision entre bases
 *    ✅ Sécurité: pas de guessing d'IDs séquentiels
 *    ✅ Distribution: génération sans DB
 *    ❌ Trade-off: index UUID légèrement moins performant que BIGINT
 * 
 * 2. @Audited (Hibernate Envers):
 *    ✅ Audit trail automatique (qui a modifié quoi quand)
 *    ✅ Table tenant_audit générée automatiquement
 *    ✅ Query historique: tenant.atRevision(5)
 *    ✅ RGPD compliance: traçabilité complète
 * 
 * 3. Soft Delete avec @SQLDelete + @Where:
 *    ✅ Override DELETE SQL pour SET deleted_at = NOW()
 *    ✅ @Where filtre automatiquement dans SELECT
 *    ✅ Recovery possible (annuler suppression)
 *    ⚠️ ATTENTION: @Where s'applique aussi aux associations, peut causer N+1
 * 
 * 4. @Enumerated(STRING) pour tier/status:
 *    ✅ Lisible en DB (pas de codes numériques)
 *    ✅ Refactoring-safe (ajout valeurs sans migration)
 *    ❌ Trade-off: stockage +bytes vs INT
 * 
 * 5. @Type(JsonBinaryType) pour settings JSONB:
 *    ✅ Flexibilité: pas de migration pour nouveaux settings
 *    ✅ PostgreSQL indexing (GIN index sur JSONB)
 *    ⚠️ Pas de validation schéma DB (faire en code)
 * 
 * 6. @OneToMany LAZY (défaut):
 *    ✅ Évite eager loading systématique
 *    ✅ Utiliser @EntityGraph quand besoin
 *    ❌ PIÈGE: Ne pas accéder en dehors transaction (LazyInitializationException)
 */
@Entity
@Table(
    name = "tenants",
    indexes = {
        @Index(name = "idx_tenants_subdomain", columnList = "subdomain"),
        @Index(name = "idx_tenants_status", columnList = "status"),
        @Index(name = "idx_tenants_tier", columnList = "tier")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_subdomain", columnNames = "subdomain"),
        @UniqueConstraint(name = "uk_custom_domain", columnNames = "custom_domain")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited // Hibernate Envers: audit automatique
@SQLDelete(sql = "UPDATE tenants SET deleted_at = NOW() WHERE id = ? AND version = ?")
@Where(clause = "deleted_at IS NULL")
public class Tenant extends BaseEntity {

    @NotBlank(message = "Tenant name is required")
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Subdomain pour accès: {subdomain}.shopstream.com
     * 
     * VALIDATION:
     * - Lowercase uniquement
     * - Alphanumeric + hyphens
     * - 3-30 caractères
     */
    @NotBlank(message = "Subdomain is required")
    @Pattern(
        regexp = "^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$",
        message = "Subdomain must be 3-30 lowercase alphanumeric characters or hyphens"
    )
    @Column(name = "subdomain", unique = true, nullable = false, length = 100)
    private String subdomain;

    /**
     * Custom domain (optionnel): www.mybrand.com
     */
    @Column(name = "custom_domain", unique = true, length = 255)
    private String customDomain;

    /**
     * Plan tarifaire.
     * 
     * POURQUOI enum:
     * - Type-safe en code
     * - Strategy Pattern pour comportements différents par tier
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 50)
    @Builder.Default
    private TenantTier tier = TenantTier.FREE;

    /**
     * Status du tenant.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private TenantStatus status = TenantStatus.ACTIVE;

    /**
     * Configuration flexible en JSONB.
     * 
     * EXEMPLE:
     * {
     *   "theme": {
     *     "primaryColor": "#FF5733",
     *     "logo": "https://cdn.../logo.png"
     *   },
     *   "features": {
     *     "recommendations": true,
     *     "reviews": false
     *   }
     * }
     * 
     * ALTERNATIVE si schéma stable:
     * - Créer table tenant_settings avec colonnes typées
     * - Meilleure validation, index, performance
     */
    @Column(name = "settings", columnDefinition = "jsonb")
    private String settings; // Stocker JSON string, parser en DTO

    /**
     * Association avec subscription (plan actuel).
     * 
     * POURQUOI separate table:
     * - Historique des subscriptions (plusieurs lignes par tenant)
     * - Query facile: "subscriptions actives", "revenue mensuel"
     */
    @OneToMany(
        mappedBy = "tenant",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @Builder.Default
    private Set<TenantSubscription> subscriptions = new HashSet<>();

    /**
     * Association avec users du tenant.
     */
    @OneToMany(
        mappedBy = "tenant",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @Builder.Default
    private Set<TenantUser> users = new HashSet<>();

    // Helper methods pour gérer les associations bidirectionnelles

    public void addSubscription(TenantSubscription subscription) {
        subscriptions.add(subscription);
        subscription.setTenant(this);
    }

    public void removeSubscription(TenantSubscription subscription) {
        subscriptions.remove(subscription);
        subscription.setTenant(null);
    }

    public void addUser(TenantUser user) {
        users.add(user);
        user.setTenant(this);
    }

    public void removeUser(TenantUser user) {
        users.remove(user);
        user.setTenant(null);
    }

    /**
     * Business logic: upgrade tier.
     * 
     * POURQUOI dans l'entité:
     * - Domain Model Pattern (entité pas anémique)
     * - Validation métier centralisée
     * - Impossible d'avoir état incohérent
     */
    public void upgradeTier(TenantTier newTier) {
        if (newTier.ordinal() < this.tier.ordinal()) {
            throw new IllegalArgumentException(
                "Cannot downgrade tier automatically. Contact support."
            );
        }
        this.tier = newTier;
    }

    public void suspend(String reason) {
        this.status = TenantStatus.SUSPENDED;
        // TODO: Publier event tenant.suspended
    }

    public void activate() {
        this.status = TenantStatus.ACTIVE;
        // TODO: Publier event tenant.activated
    }
}
