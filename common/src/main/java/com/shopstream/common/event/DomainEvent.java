package com.shopstream.common.event;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class pour tous les Domain Events.
 * 
 * POURQUOI cette structure:
 * - Champs communs à tous events (id, timestamp, version)
 * - JsonTypeInfo pour polymorphisme lors désérialisation Kafka
 * - eventVersion pour évolution schéma (v1, v2...)
 * - aggregateId pour lier event à son aggregate root
 * 
 * PATTERN: Domain Event Pattern (DDD)
 * 
 * KAFKA SERIALIZATION:
 * - Option 1: JSON avec JsonTypeInfo (flexible, human-readable)
 * - Option 2: Avro avec Schema Registry (type-safe, compact, backward compatible)
 * 
 * POURQUOI Avro pour PRODUCTION:
 * ✅ Schéma validation automatique
 * ✅ Backward/Forward compatibility
 * ✅ Taille messages réduite (binaire)
 * ✅ Documentation intégrée
 * ✅ Code generation
 * 
 * POURQUOI JSON pour DEV/DEBUG:
 * ✅ Human-readable dans Kafka UI
 * ✅ Pas besoin Schema Registry
 * ✅ Flexible pour prototypage
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "@type"
)
public abstract class DomainEvent {

    /**
     * Event ID unique (pour idempotence).
     */
    private UUID eventId;

    /**
     * Timestamp de création de l'event.
     */
    private Instant timestamp;

    /**
     * ID de l'aggregate root concerné.
     */
    private UUID aggregateId;

    /**
     * Version de l'event (pour Event Sourcing).
     * 
     * POURQUOI:
     * - Ordre garanti des events pour un même aggregate
     * - Détection de gaps (event manquant)
     * - Optimistic concurrency control
     */
    private Long eventVersion;

    /**
     * Version du schéma de l'event (pour évolution).
     * 
     * EXEMPLE:
     * - V1: OrderCreatedEvent avec champs basiques
     * - V2: OrderCreatedEvent avec nouveau champ "promoCode"
     * 
     * Consumer peut gérer plusieurs versions:
     * if (event.schemaVersion == 1) { ... }
     * else if (event.schemaVersion == 2) { ... }
     */
    private Integer schemaVersion;

    /**
     * Tenant ID pour multi-tenancy.
     */
    private UUID tenantId;

    /**
     * Correlation ID pour distributed tracing.
     */
    private String correlationId;

    /**
     * Type de l'event (nom de la classe).
     */
    public String getEventType() {
        return this.getClass().getSimpleName();
    }
}
