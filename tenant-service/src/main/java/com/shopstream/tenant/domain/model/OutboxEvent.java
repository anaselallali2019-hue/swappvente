package com.shopstream.tenant.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Table Outbox pour Transactional Outbox Pattern.
 * 
 * POURQUOI OUTBOX PATTERN:
 * 
 * PROBLÈME (sans Outbox):
 * ```java
 * @Transactional
 * void createTenant(Tenant tenant) {
 *     tenantRepository.save(tenant);           // 1. DB write
 *     kafkaTemplate.send("tenant.created", event); // 2. Kafka write
 * }
 * ```
 * ❌ Si Kafka fail après DB commit → pas d'event publié
 * ❌ Si DB rollback après Kafka success → event publié pour tenant inexistant
 * ❌ Dual Write Problem: pas d'atomicité DB + Kafka
 * 
 * SOLUTION (avec Outbox):
 * ```java
 * @Transactional
 * void createTenant(Tenant tenant) {
 *     tenantRepository.save(tenant);               // 1. DB write
 *     outboxRepository.save(new OutboxEvent(...)); // 2. DB write (SAME transaction)
 *     // Kafka sera notifié par Debezium CDC
 * }
 * ```
 * ✅ Atomicité garantie: DB transaction couvre tenant + outbox
 * ✅ Debezium lit outbox via PostgreSQL logical replication
 * ✅ Exactly-once delivery: event publié seulement si DB commit OK
 * 
 * ARCHITECTURE:
 * 1. Application écrit dans outbox_events (transaction DB)
 * 2. Debezium CDC lit PostgreSQL WAL (Write-Ahead Log)
 * 3. Debezium publie vers Kafka
 * 4. Application marque processed_at (optionnel, pour cleanup)
 * 
 * ALTERNATIVE:
 * - Polling Outbox (scheduled task lit table toutes les N secondes)
 * - Moins performant que CDC mais plus simple setup
 * 
 * CONFIGURATION REQUISE:
 * - PostgreSQL: wal_level = logical
 * - Debezium Connector: PostgreSQL Source Connector
 * - Kafka Connect: cluster séparé
 */
@Entity
@Table(
    name = "outbox_events",
    indexes = {
        @Index(
            name = "idx_outbox_not_processed",
            columnList = "created_at",
            // Partial index pour performance (seulement events non traités)
            // NOTE: JPA ne supporte pas WHERE clause dans @Index,
            // créer manuellement dans migration Flyway
            unique = false
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * ID de l'aggregate concerné (tenant_id, order_id, etc.).
     */
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    /**
     * Type de l'aggregate (Tenant, Order, etc.).
     */
    @Column(name = "aggregate_type", length = 100, nullable = false)
    private String aggregateType;

    /**
     * Type de l'event (TenantCreated, TenantUpdated, etc.).
     */
    @Column(name = "event_type", nullable = false)
    private String eventType;

    /**
     * Payload JSON de l'event.
     * 
     * POURQUOI JSONB:
     * - Flexible (schéma event peut évoluer)
     * - Indexable (GIN index PostgreSQL)
     * - Query dans Kafka UI plus simple qu'Avro
     * 
     * ALTERNATIVE:
     * - Avro binaire si performance critique
     */
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Timestamp quand event a été traité (optionnel).
     * 
     * UTILITÉ:
     * - Cleanup périodique (supprimer events > 7 jours)
     * - Monitoring (délai entre création et traitement)
     */
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public void markProcessed() {
        this.processedAt = LocalDateTime.now();
    }
}
