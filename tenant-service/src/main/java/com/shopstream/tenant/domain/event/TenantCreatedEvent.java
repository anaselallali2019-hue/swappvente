package com.shopstream.tenant.domain.event;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.shopstream.common.event.DomainEvent;
import com.shopstream.tenant.domain.model.TenantTier;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Event publié quand un nouveau tenant est créé.
 * 
 * CONSUMERS ATTENDUS:
 * - product-catalog-service: initialiser catalog vide
 * - inventory-service: créer warehouse par défaut
 * - notification-service: envoyer email welcome
 * - analytics-service: créer dashboard tenant
 * 
 * TOPIC KAFKA: tenant.created
 * 
 * AVRO SCHEMA (alternative à JSON):
 * ```json
 * {
 *   "type": "record",
 *   "name": "TenantCreatedEvent",
 *   "fields": [
 *     {"name": "tenantId", "type": "string"},
 *     {"name": "name", "type": "string"},
 *     {"name": "subdomain", "type": "string"},
 *     {"name": "tier", "type": "string"}
 *   ]
 * }
 * ```
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@JsonTypeName("TenantCreatedEvent")
public class TenantCreatedEvent extends DomainEvent {
    
    private String name;
    private String subdomain;
    private TenantTier tier;
}
