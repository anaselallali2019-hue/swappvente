package com.shopstream.tenant.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopstream.common.exception.ResourceNotFoundException;
import com.shopstream.tenant.application.dto.*;
import com.shopstream.tenant.application.mapper.TenantMapper;
import com.shopstream.tenant.domain.event.TenantCreatedEvent;
import com.shopstream.tenant.domain.event.TenantDeletedEvent;
import com.shopstream.tenant.domain.event.TenantUpdatedEvent;
import com.shopstream.tenant.domain.factory.TenantFactory;
import com.shopstream.tenant.domain.model.OutboxEvent;
import com.shopstream.tenant.domain.model.Tenant;
import com.shopstream.tenant.domain.repository.OutboxEventRepository;
import com.shopstream.tenant.domain.repository.TenantRepository;
import com.shopstream.tenant.domain.repository.TenantSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service métier pour gestion des tenants.
 * 
 * ARCHITECTURE EN COUCHES:
 * Controller -> Service -> Repository -> Database
 * 
 * RESPONSABILITÉS DU SERVICE:
 * ✅ Logique métier
 * ✅ Orchestration (appel plusieurs repositories)
 * ✅ Transactions
 * ✅ Publication events
 * ✅ Cache management
 * 
 * CE QUI NE DOIT PAS ÊTRE ICI:
 * ❌ Validation HTTP (dans controller/DTO)
 * ❌ Queries complexes (dans repository)
 * ❌ Formatting réponse (dans controller)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true) // Défaut: lecture seule pour optimisation
public class TenantService {

    private final TenantRepository tenantRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TenantMapper tenantMapper;
    private final TenantFactory tenantFactory;
    private final ObjectMapper objectMapper;

    /**
     * F1.1: Création d'un nouveau tenant.
     * 
     * DÉCISIONS TECHNIQUES:
     * 
     * 1. @Transactional(propagation = REQUIRED):
     *    - Nouvelle transaction si pas existante
     *    - Rejoint transaction existante sinon
     *    - REQUIRED vs REQUIRES_NEW: REQUIRED est standard pour business logic
     * 
     * 2. Factory Pattern:
     *    - TenantFactory.create() encapsule logique création complexe
     *    - Initialisation différente selon tier (Free vs Pro vs Enterprise)
     *    - Configuration par défaut, validation métier
     * 
     * 3. Outbox Pattern:
     *    - Save tenant + outbox event dans MÊME transaction
     *    - Garantie atomicité: si tenant save fail, pas d'event
     *    - Debezium CDC publie event vers Kafka après commit DB
     * 
     * 4. Pas de cache:
     *    - Opération write, pas besoin cache
     *    - Invalide cache search après création
     * 
     * POURQUOI PAS Kafka direct:
     * ```java
     * tenantRepository.save(tenant);
     * kafkaTemplate.send("tenant.created", event); // ❌ DUAL WRITE PROBLEM
     * ```
     * - Si Kafka fail après DB commit: pas d'event publié
     * - Si DB rollback après Kafka success: event orphelin
     */
    @Transactional(propagation = Propagation.REQUIRED)
    @CacheEvict(value = "tenants", allEntries = true) // Invalide cache recherche
    public TenantResponse createTenant(CreateTenantRequest request) {
        log.info("Creating tenant with subdomain: {}", request.getSubdomain());

        // Validation business: subdomain unique
        if (tenantRepository.existsBySubdomain(request.getSubdomain())) {
            throw new IllegalArgumentException(
                "Subdomain '" + request.getSubdomain() + "' already exists"
            );
        }

        // Factory Pattern: création avec configuration initiale selon tier
        Tenant tenant = tenantFactory.createTenant(request);

        // Persist tenant
        Tenant savedTenant = tenantRepository.save(tenant);

        // Outbox Pattern: publish event via outbox table
        publishTenantCreatedEvent(savedTenant);

        log.info("Tenant created successfully: id={}, subdomain={}", 
            savedTenant.getId(), savedTenant.getSubdomain());

        return tenantMapper.toResponse(savedTenant);
    }

    /**
     * F1.2: Récupération liste tenants avec filtres complexes.
     * 
     * DÉCISIONS TECHNIQUES:
     * 
     * 1. Criteria API + Specifications:
     *    - Filtres dynamiques (0-N critères)
     *    - Type-safe, compile-time checking
     *    - Composition: spec1.and(spec2).and(spec3)
     *    - POURQUOI PAS Query Methods: trop de combinaisons possibles
     *    - POURQUOI PAS JPQL: pas dynamique
     * 
     * 2. Pagination:
     *    - Pageable pour limit/offset
     *    - Page<T> pour résultats + metadata (total, pages)
     *    - IMPORTANT: toujours paginer (éviter load millions rows)
     * 
     * 3. Cache:
     *    - @Cacheable page 1 seulement (requête la plus fréquente)
     *    - Key: critères + page number
     *    - TTL: 5 minutes (config dans application.yml)
     *    - POURQUOI Redis: cache partagé entre instances
     * 
     * 4. @EntityGraph:
     *    - Fetch subscriptions en une requête (éviter N+1)
     *    - Optionnel ici car TenantResponse pas besoin subscriptions
     *    - Activer si frontend demande relations
     */
    @Transactional(readOnly = true)
    @Cacheable(
        value = "tenants",
        key = "#criteria.toString() + '-' + #pageable.pageNumber",
        condition = "#pageable.pageNumber == 0" // Cache page 1 only
    )
    public Page<TenantResponse> searchTenants(
        TenantSearchCriteria criteria,
        Pageable pageable
    ) {
        log.debug("Searching tenants with criteria: {}", criteria);

        // Build dynamic query avec Specifications
        Specification<Tenant> spec = Specification.where(null);

        if (criteria.getName() != null) {
            spec = spec.and(TenantSpecifications.nameLike(criteria.getName()));
        }
        if (criteria.getStatus() != null) {
            spec = spec.and(TenantSpecifications.hasStatus(criteria.getStatus()));
        }
        if (criteria.getTier() != null) {
            spec = spec.and(TenantSpecifications.hasTier(criteria.getTier()));
        }
        if (criteria.getCreatedAfter() != null) {
            spec = spec.and(TenantSpecifications.createdAfter(criteria.getCreatedAfter()));
        }
        if (criteria.getCreatedBefore() != null) {
            spec = spec.and(TenantSpecifications.createdBefore(criteria.getCreatedBefore()));
        }
        if (criteria.getMinUsers() != null) {
            spec = spec.and(TenantSpecifications.hasMinUsers(criteria.getMinUsers()));
        }
        if (criteria.getSubdomain() != null) {
            spec = spec.and(TenantSpecifications.subdomainContains(criteria.getSubdomain()));
        }

        // Execute query avec pagination
        Page<Tenant> tenants = tenantRepository.findAll(spec, pageable);

        // Convert to DTO
        return tenants.map(tenantMapper::toResponse);
    }

    /**
     * F1.3: Récupération détail tenant par ID.
     */
    @Transactional(readOnly = true)
    public TenantResponse getTenantById(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));
        
        return tenantMapper.toResponse(tenant);
    }

    /**
     * F1.4: Mise à jour configuration tenant.
     * 
     * DÉCISIONS TECHNIQUES:
     * 
     * 1. Optimistic Locking (@Version):
     *    - Évite conflits si 2 admins modifient simultanément
     *    - JPA check version avant UPDATE
     *    - Si version DB ≠ version entity: OptimisticLockException
     *    - Frontend doit gérer exception et refresh
     * 
     * 2. @Transactional(isolation = REPEATABLE_READ):
     *    - Plus strict que READ_COMMITTED (défaut PostgreSQL)
     *    - Garantit même lecture si re-query dans transaction
     *    - POURQUOI: éviter phantom reads lors validation
     * 
     * 3. Partial Update:
     *    - MapStruct avec nullValuePropertyMappingStrategy = IGNORE
     *    - Update seulement champs non-null dans request
     *    - Évite override accidentel
     * 
     * 4. Validation custom:
     *    - customDomain unique (check DB)
     *    - tier upgrade only (pas downgrade auto)
     */
    @Transactional(
        propagation = Propagation.REQUIRED,
        isolation = Isolation.REPEATABLE_READ
    )
    @CacheEvict(value = "tenants", allEntries = true)
    public TenantResponse updateTenant(UUID tenantId, UpdateTenantRequest request) {
        log.info("Updating tenant: {}", tenantId);

        // Load tenant (avec lock optimistic)
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        // Validation: customDomain unique
        if (request.getCustomDomain() != null &&
            !request.getCustomDomain().equals(tenant.getCustomDomain()) &&
            tenantRepository.existsByCustomDomain(request.getCustomDomain())) {
            throw new IllegalArgumentException(
                "Custom domain '" + request.getCustomDomain() + "' already exists"
            );
        }

        // Validation: tier upgrade only
        if (request.getTier() != null && request.getTier() != tenant.getTier()) {
            tenant.upgradeTier(request.getTier()); // Business logic in entity
        }

        // MapStruct partial update
        tenantMapper.updateEntity(request, tenant);

        // Save (version auto-incrémenté par JPA)
        Tenant updatedTenant = tenantRepository.save(tenant);

        // Publish event
        publishTenantUpdatedEvent(updatedTenant);

        log.info("Tenant updated successfully: {}", tenantId);
        return tenantMapper.toResponse(updatedTenant);
    }

    /**
     * F1.5: Soft delete tenant.
     * 
     * DÉCISIONS TECHNIQUES:
     * 
     * 1. Soft Delete:
     *    - @SQLDelete override DELETE SQL
     *    - SET deleted_at = NOW() au lieu de DELETE
     *    - @Where filtre automatiquement dans SELECT
     * 
     * 2. RGPD Compliance:
     *    - Garder données 30 jours
     *    - Scheduled task hard delete après 30j
     *    - User peut demander suppression immédiate
     * 
     * 3. Event:
     *    - tenant.deleted publié immédiatement
     *    - Autres services désactivent ressources associées
     */
    @Transactional(propagation = Propagation.REQUIRED)
    @CacheEvict(value = "tenants", allEntries = true)
    public void deleteTenant(UUID tenantId) {
        log.info("Soft deleting tenant: {}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

        // Soft delete (met deleted_at)
        tenant.softDelete();
        tenantRepository.save(tenant);

        // Publish event
        publishTenantDeletedEvent(tenant);

        log.info("Tenant soft deleted: {}", tenantId);
    }

    // ========== Private Helper Methods ==========

    private void publishTenantCreatedEvent(Tenant tenant) {
        try {
            TenantCreatedEvent event = TenantCreatedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(java.time.Instant.now())
                .aggregateId(tenant.getId())
                .tenantId(tenant.getId())
                .name(tenant.getName())
                .subdomain(tenant.getSubdomain())
                .tier(tenant.getTier())
                .build();

            OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateId(tenant.getId())
                .aggregateType("Tenant")
                .eventType("TenantCreated")
                .payload(objectMapper.writeValueAsString(event))
                .build();

            outboxEventRepository.save(outboxEvent);

            log.debug("TenantCreated event saved to outbox: tenantId={}", tenant.getId());
        } catch (Exception e) {
            log.error("Failed to publish TenantCreated event", e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }

    private void publishTenantUpdatedEvent(Tenant tenant) {
        try {
            TenantUpdatedEvent event = TenantUpdatedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(java.time.Instant.now())
                .aggregateId(tenant.getId())
                .tenantId(tenant.getId())
                .name(tenant.getName())
                .customDomain(tenant.getCustomDomain())
                .tier(tenant.getTier())
                .build();

            OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateId(tenant.getId())
                .aggregateType("Tenant")
                .eventType("TenantUpdated")
                .payload(objectMapper.writeValueAsString(event))
                .build();

            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to publish TenantUpdated event", e);
        }
    }

    private void publishTenantDeletedEvent(Tenant tenant) {
        try {
            TenantDeletedEvent event = TenantDeletedEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(java.time.Instant.now())
                .aggregateId(tenant.getId())
                .tenantId(tenant.getId())
                .build();

            OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateId(tenant.getId())
                .aggregateType("Tenant")
                .eventType("TenantDeleted")
                .payload(objectMapper.writeValueAsString(event))
                .build();

            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to publish TenantDeleted event", e);
        }
    }
}
