package com.shopstream.tenant.application.service;

import com.shopstream.tenant.application.dto.TenantStatsResponse;
import com.shopstream.tenant.domain.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service pour statistiques globales tenants (Dashboard admin).
 * 
 * F1.3: Statistiques avec Native Query complexes.
 * 
 * POURQUOI NATIVE QUERY (et pas JPA ou Criteria):
 * 
 * ✅ Window Functions (OVER, PARTITION BY)
 * ✅ CTEs (Common Table Expressions - WITH clauses)
 * ✅ Agrégations complexes (GROUP BY + ROLLUP)
 * ✅ PostgreSQL-specific optimizations
 * 
 * ❌ Pas portable vers autres DB (MySQL, Oracle)
 * ❌ Pas type-safe (String SQL)
 * ❌ Pas de validation compile-time
 * 
 * ALTERNATIVE:
 * - JOOQ: type-safe SQL mais plus de setup
 * - Materialized View + simple query
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TenantStatsService {

    @PersistenceContext
    private final EntityManager entityManager;
    
    private final TenantRepository tenantRepository;

    /**
     * Calculer statistiques globales avec Native Query complexe.
     * 
     * NATIVE QUERY FEATURES UTILISÉES:
     * 1. CTEs (WITH): décomposer query complexe en étapes lisibles
     * 2. Window Functions: LAG() pour comparer avec mois précédent
     * 3. DATE functions: DATE_TRUNC, INTERVAL
     * 4. Agrégations: COUNT, SUM, AVG avec GROUP BY
     * 
     * CACHE:
     * - @Cacheable avec TTL 10 minutes
     * - POURQUOI: données agrégées changent peu, calcul coûteux
     * - Key: fixe "global-stats" (une seule entrée cache)
     * 
     * OPTIMISATION:
     * - Alternative: Materialized View refresh toutes les 5 min
     * - Évite calcul à chaque requête
     */
    @Cacheable(value = "tenant-stats", key = "'global-stats'")
    public TenantStatsResponse getGlobalStats() {
        log.info("Calculating global tenant statistics");

        // Query 1: Total tenants
        Long totalTenants = (Long) entityManager
            .createQuery("SELECT COUNT(t) FROM Tenant t")
            .getSingleResult();

        // Query 2: Tenants par tier (simple GROUP BY)
        List<Object[]> tierStats = tenantRepository.countByStatus()
            .stream()
            .map(row -> new Object[]{row[0].toString(), row[1]})
            .toList();
        
        Map<String, Long> tenantsByTier = new HashMap<>();
        for (Object[] row : tierStats) {
            tenantsByTier.put((String) row[0], ((Number) row[1]).longValue());
        }

        // Query 3: Stats avec Native Query complexe
        String sql = """
            WITH monthly_stats AS (
                -- Stats mensuelles avec comparaison mois précédent
                SELECT 
                    DATE_TRUNC('month', t.created_at) as month,
                    COUNT(*) as tenant_count,
                    SUM(s.monthly_revenue) as revenue,
                    -- LAG: accès valeur ligne précédente (window function)
                    LAG(COUNT(*)) OVER (ORDER BY DATE_TRUNC('month', t.created_at)) as prev_month_count,
                    LAG(SUM(s.monthly_revenue)) OVER (ORDER BY DATE_TRUNC('month', t.created_at)) as prev_month_revenue
                FROM tenants t
                LEFT JOIN tenant_subscriptions s ON t.id = s.tenant_id 
                    AND s.end_date IS NULL  -- subscription active
                WHERE t.deleted_at IS NULL
                GROUP BY DATE_TRUNC('month', t.created_at)
            ),
            current_month AS (
                -- Stats mois en cours
                SELECT 
                    tenant_count as new_tenants,
                    revenue as current_revenue,
                    prev_month_revenue,
                    -- Calcul croissance percentage
                    CASE 
                        WHEN prev_month_revenue > 0 
                        THEN ((revenue - prev_month_revenue) / prev_month_revenue * 100)
                        ELSE NULL
                    END as growth_percentage
                FROM monthly_stats
                WHERE month = DATE_TRUNC('month', CURRENT_DATE)
            ),
            active_tenants AS (
                -- Tenants actifs (au moins 1 commande dans 30 jours)
                -- NOTE: Nécessite table orders dans autre service
                -- Ici simplifié: tenants avec subscription active
                SELECT COUNT(DISTINCT t.id) as active_count
                FROM tenants t
                JOIN tenant_subscriptions s ON t.id = s.tenant_id
                WHERE s.end_date IS NULL OR s.end_date > CURRENT_DATE
            )
            SELECT 
                (SELECT COALESCE(SUM(monthly_revenue), 0) FROM tenant_subscriptions WHERE end_date IS NULL) as total_revenue,
                (SELECT COALESCE(growth_percentage, 0) FROM current_month) as growth_percentage,
                (SELECT COALESCE(new_tenants, 0) FROM current_month) as new_tenants_this_month,
                (SELECT active_count FROM active_tenants) as active_tenants
            """;

        Query query = entityManager.createNativeQuery(sql);
        Object[] result = (Object[]) query.getSingleResult();

        // Build response
        return TenantStatsResponse.builder()
            .totalTenants(totalTenants)
            .tenantsByTier(tenantsByTier)
            .tenantsByStatus(new HashMap<>()) // TODO: implement
            .totalMonthlyRevenue(
                result[0] != null ? new BigDecimal(result[0].toString()) : BigDecimal.ZERO
            )
            .monthlyGrowthPercentage(
                result[1] != null ? new BigDecimal(result[1].toString()) : BigDecimal.ZERO
            )
            .newTenantsThisMonth(
                result[2] != null ? ((Number) result[2]).longValue() : 0L
            )
            .activeTenantsLast30Days(
                result[3] != null ? ((Number) result[3]).longValue() : 0L
            )
            .build();
    }

    /**
     * EXEMPLE: Materialized View alternative.
     * 
     * POURQUOI Materialized View:
     * ✅ Pré-calcul des agrégations
     * ✅ Query ultra-rapide (simple SELECT)
     * ✅ Refresh automatique (cron ou trigger)
     * 
     * COMMENT:
     * 1. Créer view dans migration Flyway:
     * ```sql
     * CREATE MATERIALIZED VIEW tenant_stats_mv AS
     * SELECT ... (query complexe ci-dessus)
     * ```
     * 
     * 2. Refresh périodique:
     * ```sql
     * REFRESH MATERIALIZED VIEW CONCURRENTLY tenant_stats_mv;
     * ```
     * 
     * 3. Query service:
     * ```java
     * entityManager.createNativeQuery("SELECT * FROM tenant_stats_mv")
     * ```
     * 
     * CONCURRENTLY:
     * - Permet refresh sans bloquer lectures
     * - Nécessite UNIQUE index sur view
     */
}
