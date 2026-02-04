package com.shopstream.tenant.domain.repository;

import com.shopstream.tenant.domain.model.Tenant;
import com.shopstream.tenant.domain.model.TenantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository pour Tenant avec différentes stratégies query.
 * 
 * DÉMONSTRATION DES APPROCHES:
 * 1. Query Methods (simple, lisible)
 * 2. @Query JPQL (query complexes)
 * 3. @Query Native SQL (PostgreSQL-specific)
 * 4. Specifications (filtres dynamiques) - voir TenantSpecifications
 * 5. EntityGraph (éviter N+1)
 */
@Repository
public interface TenantRepository extends 
    JpaRepository<Tenant, UUID>,
    JpaSpecificationExecutor<Tenant> { // Pour Specifications (Criteria API)

    /**
     * APPROCHE 1: Query Method (Spring Data génère automatiquement).
     * 
     * QUAND UTILISER:
     * ✅ Query simple avec 1-3 critères
     * ✅ Lisible et maintenable
     * ❌ Nom méthode devient long avec beaucoup de critères
     */
    Optional<Tenant> findBySubdomain(String subdomain);

    Optional<Tenant> findByCustomDomain(String customDomain);

    List<Tenant> findByStatus(TenantStatus status);

    /**
     * APPROCHE 2: @Query JPQL (query custom mais DB-agnostic).
     * 
     * QUAND UTILISER:
     * ✅ Query complexe mais portable (MySQL, PostgreSQL)
     * ✅ Projections custom
     * ❌ Pas d'accès aux fonctionnalités DB-specific
     */
    @Query("SELECT t FROM Tenant t WHERE t.status = :status AND t.tier = :tier")
    List<Tenant> findByStatusAndTier(
        @Param("status") TenantStatus status,
        @Param("tier") String tier
    );

    /**
     * APPROCHE 3: @EntityGraph pour éviter N+1 queries.
     * 
     * PROBLÈME (sans @EntityGraph):
     * ```
     * List<Tenant> tenants = repository.findAll(); // 1 query
     * for (Tenant t : tenants) {
     *     t.getSubscriptions().size(); // N queries (LazyInitializationException si hors transaction)
     * }
     * ```
     * 
     * SOLUTION:
     * - @EntityGraph fait un JOIN FETCH
     * - 1 seule query avec LEFT JOIN
     */
    @EntityGraph(attributePaths = {"subscriptions", "users"})
    @Query("SELECT t FROM Tenant t WHERE t.id = :id")
    Optional<Tenant> findByIdWithRelations(@Param("id") UUID id);

    /**
     * APPROCHE 4: Native Query pour PostgreSQL-specific features.
     * 
     * QUAND UTILISER:
     * ✅ Window functions (OVER, PARTITION BY)
     * ✅ JSONB operators
     * ✅ Full-text search (tsvector)
     * ❌ Pas portable vers autres DB
     * 
     * Ici: Recherche dans JSONB settings.
     */
    @Query(
        value = "SELECT * FROM tenants WHERE settings::jsonb @> :filter::jsonb",
        nativeQuery = true
    )
    List<Tenant> findBySettingsContaining(@Param("filter") String filter);

    /**
     * Count tenants par status (pour dashboard).
     */
    @Query("SELECT t.status, COUNT(t) FROM Tenant t GROUP BY t.status")
    List<Object[]> countByStatus();

    /**
     * Soft delete: trouver les tenants à supprimer définitivement.
     * 
     * UTILISATION:
     * - Scheduled task toutes les nuits
     * - Hard delete si deleted_at > 30 jours
     */
    @Query("SELECT t FROM Tenant t WHERE t.deletedAt IS NOT NULL AND t.deletedAt < :threshold")
    List<Tenant> findDeletedBefore(@Param("threshold") LocalDateTime threshold);

    /**
     * Check si subdomain existe (pour validation).
     */
    boolean existsBySubdomain(String subdomain);

    boolean existsByCustomDomain(String customDomain);
}
