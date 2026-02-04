package com.shopstream.tenant.domain.event;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.shopstream.common.event.DomainEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Event publié quand un tenant est supprimé (soft delete).
 * 
 * CONSUMERS ATTENDUS:
 * - product-catalog-service: archiver produits
 * - order-service: bloquer nouvelles commandes
 * - notification-service: envoyer email confirmation suppression
 * 
 * TOPIC KAFKA: tenant.deleted
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@JsonTypeName("TenantDeletedEvent")
public class TenantDeletedEvent extends DomainEvent {
    // Pas de champs supplémentaires, tenantId dans base class suffit
}
