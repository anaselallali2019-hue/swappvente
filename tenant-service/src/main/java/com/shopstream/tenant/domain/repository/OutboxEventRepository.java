package com.shopstream.tenant.domain.repository;

import com.shopstream.tenant.domain.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository pour Outbox Pattern.
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Trouve events non traités (pour polling-based outbox si pas Debezium).
     */
    List<OutboxEvent> findByProcessedAtIsNullOrderByCreatedAtAsc();

    /**
     * Cleanup: supprimer events traités > 7 jours.
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.processedAt IS NOT NULL AND e.processedAt < :threshold")
    List<OutboxEvent> findProcessedBefore(LocalDateTime threshold);
}
