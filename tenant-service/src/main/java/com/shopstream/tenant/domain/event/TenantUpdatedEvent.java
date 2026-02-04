package com.shopstream.tenant.domain.event;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.shopstream.common.event.DomainEvent;
import com.shopstream.tenant.domain.model.TenantTier;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Event publié quand un tenant est mis à jour.
 * 
 * TOPIC KAFKA: tenant.updated
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@JsonTypeName("TenantUpdatedEvent")
public class TenantUpdatedEvent extends DomainEvent {
    
    private String name;
    private String customDomain;
    private TenantTier tier;
}
