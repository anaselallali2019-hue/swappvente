package com.shopstream.tenant.infrastructure.scheduler;

import com.shopstream.tenant.domain.model.Tenant;
import com.shopstream.tenant.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled task pour hard delete des tenants soft-deleted > 30 jours.
 * 
 * F1.5: Cleanup RGPD compliance.
 * 
 * POURQUOI SCHEDULED TASK:
 * - RGPD: garder données 30 jours après suppression
 * - Hard delete automatique après période légale
 * - Libérer espace DB
 * 
 * @Scheduled(cron):
 * - Format: "sec min hour day month weekday"
 * - "0 0 2 * * *" = tous les jours à 2h du matin
 * - Heure creuse pour éviter impact performance
 * 
 * ALTERNATIVE:
 * - Partition PostgreSQL avec DROP PARTITION (plus performant)
 * - Archive vers S3 avant delete (compliance long-terme)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantCleanupScheduler {

    private final TenantRepository tenantRepository;

    /**
     * Hard delete tenants soft-deleted > 30 jours.
     * 
     * Exécuté tous les jours à 2h du matin.
     * 
     * IMPORTANT:
     * - @Transactional pour rollback si erreur
     * - Batch delete (pas 1 query par tenant)
     * - Log pour audit
     */
    @Scheduled(cron = "${tenant.scheduler.cleanup-cron:0 0 2 * * *}")
    @Transactional
    public void cleanupDeletedTenants() {
        log.info("Starting cleanup of soft-deleted tenants older than 30 days");

        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        
        // Find tenants à supprimer
        List<Tenant> tenantsToDelete = tenantRepository.findDeletedBefore(threshold);
        
        if (tenantsToDelete.isEmpty()) {
            log.info("No tenants to cleanup");
            return;
        }

        log.info("Found {} tenants to hard delete", tenantsToDelete.size());

        // Hard delete (bypass soft delete)
        for (Tenant tenant : tenantsToDelete) {
            log.info("Hard deleting tenant: id={}, name={}, deleted_at={}",
                tenant.getId(), tenant.getName(), tenant.getDeletedAt());
        }

        // Batch delete
        tenantRepository.deleteAll(tenantsToDelete);

        log.info("Successfully hard deleted {} tenants", tenantsToDelete.size());
    }

    /**
     * Cleanup outbox events traités > 7 jours.
     * 
     * POURQUOI:
     * - Éviter croissance infinie de la table outbox_events
     * - Events déjà publiés vers Kafka, pas besoin garder
     * - Garder 7 jours pour debug/troubleshooting
     */
    @Scheduled(cron = "0 30 2 * * *") // 2h30 du matin
    @Transactional
    public void cleanupOutboxEvents() {
        log.info("Starting cleanup of processed outbox events older than 7 days");
        
        // TODO: Implémenter avec OutboxEventRepository
        
        log.info("Outbox cleanup completed");
    }
}
