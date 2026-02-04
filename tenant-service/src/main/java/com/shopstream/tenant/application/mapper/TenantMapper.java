package com.shopstream.tenant.application.mapper;

import com.shopstream.tenant.application.dto.CreateTenantRequest;
import com.shopstream.tenant.application.dto.TenantResponse;
import com.shopstream.tenant.application.dto.UpdateTenantRequest;
import com.shopstream.tenant.domain.model.Tenant;
import org.mapstruct.*;

/**
 * MapStruct mapper pour conversion DTO <-> Entity.
 * 
 * POURQUOI MAPSTRUCT (vs manual mapping ou ModelMapper):
 * 
 * ✅ Génération code compile-time (pas reflection runtime)
 * ✅ Performance native (aucun overhead)
 * ✅ Type-safe (erreurs à la compilation)
 * ✅ Lisible (code généré visible)
 * ✅ Custom mapping facile (@Mapping)
 * 
 * COMPARAISON:
 * 
 * MANUAL MAPPING:
 * ```java
 * TenantResponse response = new TenantResponse();
 * response.setId(tenant.getId());
 * response.setName(tenant.getName());
 * // ... 20 lignes de code boilerplate
 * ```
 * ❌ Verbeux, erreur-prone
 * 
 * MODELMAPPER (reflection-based):
 * ```java
 * TenantResponse response = modelMapper.map(tenant, TenantResponse.class);
 * ```
 * ✅ Concis
 * ❌ Performance (reflection)
 * ❌ Erreurs runtime seulement
 * ❌ Debug difficile
 * 
 * MAPSTRUCT (code generation):
 * ```java
 * TenantResponse response = tenantMapper.toResponse(tenant);
 * ```
 * ✅ Concis
 * ✅ Performance native
 * ✅ Erreurs compile-time
 * ✅ Debug facile (code généré visible)
 * 
 * CONFIGURATION:
 * - componentModel = "spring": génère @Component (injection Spring)
 * - unmappedTargetPolicy = WARN: warning si champ oublié
 * - injectionStrategy = CONSTRUCTOR: injection par constructeur (immutable)
 */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.WARN,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR
)
public interface TenantMapper {

    /**
     * Convert CreateTenantRequest -> Tenant entity.
     * 
     * @Mapping pour champs custom:
     * - status = ACTIVE par défaut
     * - ignore id, createdAt (généré par DB/JPA)
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "subscriptions", ignore = true)
    @Mapping(target = "users", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Tenant toEntity(CreateTenantRequest request);

    /**
     * Convert Tenant entity -> TenantResponse DTO.
     * 
     * @Mapping pour champs calculés:
     * - userCount = users.size()
     * - hasActiveSubscription = check subscription active
     */
    @Mapping(target = "userCount", expression = "java(tenant.getUsers() != null ? tenant.getUsers().size() : 0)")
    @Mapping(target = "hasActiveSubscription", expression = "java(hasActiveSubscription(tenant))")
    TenantResponse toResponse(Tenant tenant);

    /**
     * Update existing Tenant entity with UpdateTenantRequest.
     * 
     * @MappingTarget: entité existante à modifier
     * nullValuePropertyMappingStrategy = IGNORE: ne pas override avec null
     * 
     * POURQUOI IGNORE null:
     * - Partial update (seulement champs présents dans request)
     * - Si name = null dans request, garder ancien nom
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "subscriptions", ignore = true)
    @Mapping(target = "users", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(UpdateTenantRequest request, @MappingTarget Tenant tenant);

    /**
     * Helper pour champ calculé hasActiveSubscription.
     * 
     * NOTE: MapStruct peut appeler méthodes Java dans expressions.
     */
    default boolean hasActiveSubscription(Tenant tenant) {
        if (tenant.getSubscriptions() == null || tenant.getSubscriptions().isEmpty()) {
            return false;
        }
        return tenant.getSubscriptions().stream()
            .anyMatch(sub -> sub.isActive());
    }
}
