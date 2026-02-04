package com.shopstream.tenant.domain.repository;

import com.shopstream.tenant.domain.model.Tenant;
import com.shopstream.tenant.domain.model.TenantStatus;
import com.shopstream.tenant.domain.model.TenantTier;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

/**
 * Specifications pour filtres dynamiques avec Criteria API.
 * 
 * POURQUOI CRITERIA API (et pas Query Methods ou JPQL):
 * 
 * PROBLÈME:
 * - Admin recherche tenants avec 0 à 10 filtres différents
 * - Impossible de créer une méthode pour chaque combinaison (2^10 = 1024 méthodes!)
 * 
 * SOLUTION: Specifications Pattern (Spring Data JPA)
 * - Chaque Specification = 1 critère (Predicate)
 * - Composition dynamique: spec1.and(spec2).and(spec3)...
 * - Type-safe (compile-time checking)
 * - Query construite à la volée selon filtres présents
 * 
 * EXEMPLE UTILISATION:
 * ```java
 * Specification<Tenant> spec = Specification.where(null);
 * if (nameFilter != null) {
 *     spec = spec.and(TenantSpecifications.nameLike(nameFilter));
 * }
 * if (statusFilter != null) {
 *     spec = spec.and(TenantSpecifications.hasStatus(statusFilter));
 * }
 * List<Tenant> results = repository.findAll(spec, pageable);
 * ```
 * 
 * ALTERNATIVE:
 * - QueryDSL (plus puissant mais setup complexe)
 * - JOOQ (type-safe SQL mais lock-in DB-specific)
 * 
 * PERFORMANCE:
 * ✅ Criteria API génère SQL optimal (seulement les WHERE nécessaires)
 * ✅ Pagination intégrée
 * ✅ Index DB utilisés correctement
 */
public class TenantSpecifications {

    /**
     * Filtre par nom (LIKE case-insensitive).
     * 
     * CRITERIA API EXPLAINED:
     * - Root<Tenant>: point d'entrée (FROM tenants)
     * - CriteriaBuilder: factory pour construire prédicats
     * - Predicate: condition WHERE
     */
    public static Specification<Tenant> nameLike(String name) {
        return (Root<Tenant> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            if (name == null || name.isBlank()) {
                return cb.conjunction(); // TRUE (no filter)
            }
            // SQL: WHERE LOWER(name) LIKE LOWER('%search%')
            return cb.like(
                cb.lower(root.get("name")),
                "%" + name.toLowerCase() + "%"
            );
        };
    }

    /**
     * Filtre par status.
     */
    public static Specification<Tenant> hasStatus(TenantStatus status) {
        return (root, query, cb) -> {
            if (status == null) {
                return cb.conjunction();
            }
            // SQL: WHERE status = ?
            return cb.equal(root.get("status"), status);
        };
    }

    /**
     * Filtre par tier.
     */
    public static Specification<Tenant> hasTier(TenantTier tier) {
        return (root, query, cb) -> {
            if (tier == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("tier"), tier);
        };
    }

    /**
     * Filtre par date de création (après).
     */
    public static Specification<Tenant> createdAfter(LocalDateTime date) {
        return (root, query, cb) -> {
            if (date == null) {
                return cb.conjunction();
            }
            // SQL: WHERE created_at > ?
            return cb.greaterThan(root.get("createdAt"), date);
        };
    }

    /**
     * Filtre par date de création (avant).
     */
    public static Specification<Tenant> createdBefore(LocalDateTime date) {
        return (root, query, cb) -> {
            if (date == null) {
                return cb.conjunction();
            }
            return cb.lessThan(root.get("createdAt"), date);
        };
    }

    /**
     * Filtre par nombre de users (JOIN avec table tenant_users).
     * 
     * CRITERIA API AVANCÉ:
     * - Join<Tenant, TenantUser>: JOIN tenant_users
     * - Subquery: pour COUNT
     */
    public static Specification<Tenant> hasMinUsers(Integer minUsers) {
        return (root, query, cb) -> {
            if (minUsers == null) {
                return cb.conjunction();
            }

            // Subquery pour compter users
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<Tenant> subRoot = subquery.from(Tenant.class);
            Join<Object, Object> usersJoin = subRoot.join("users");

            subquery.select(cb.count(usersJoin))
                    .where(cb.equal(subRoot.get("id"), root.get("id")));

            // SQL: WHERE (SELECT COUNT(*) FROM tenant_users WHERE tenant_id = tenants.id) >= ?
            return cb.greaterThanOrEqualTo(subquery, minUsers.longValue());
        };
    }

    /**
     * Filtre par subdomain (exact match ou LIKE).
     */
    public static Specification<Tenant> subdomainContains(String subdomain) {
        return (root, query, cb) -> {
            if (subdomain == null || subdomain.isBlank()) {
                return cb.conjunction();
            }
            return cb.like(root.get("subdomain"), "%" + subdomain + "%");
        };
    }

    /**
     * Filtre: tenants actifs seulement (non deleted).
     * 
     * NOTE: @Where(clause = "deleted_at IS NULL") filtre déjà automatiquement,
     * mais on peut override si besoin.
     */
    public static Specification<Tenant> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    /**
     * EXEMPLE: Specification complexe avec plusieurs conditions.
     * 
     * Trouve tenants:
     * - ACTIVE ou SUSPENDED
     * - Créés dans les 30 derniers jours
     * - Tier PRO ou ENTERPRISE
     */
    public static Specification<Tenant> recentPremiumTenants() {
        return (root, query, cb) -> {
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

            Predicate statusPredicate = cb.or(
                cb.equal(root.get("status"), TenantStatus.ACTIVE),
                cb.equal(root.get("status"), TenantStatus.SUSPENDED)
            );

            Predicate datePredicate = cb.greaterThan(root.get("createdAt"), thirtyDaysAgo);

            Predicate tierPredicate = cb.or(
                cb.equal(root.get("tier"), TenantTier.PRO),
                cb.equal(root.get("tier"), TenantTier.ENTERPRISE)
            );

            return cb.and(statusPredicate, datePredicate, tierPredicate);
        };
    }
}
