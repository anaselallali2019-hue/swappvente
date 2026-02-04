package com.shopstream.tenant.interfaces.rest;

import com.shopstream.tenant.application.dto.*;
import com.shopstream.tenant.application.service.TenantService;
import com.shopstream.tenant.application.service.TenantStatsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller pour gestion tenants.
 * 
 * RESPONSABILITÉS DU CONTROLLER:
 * ✅ Validation HTTP (@Valid, @PathVariable)
 * ✅ Routing (@GetMapping, @PostMapping)
 * ✅ Status codes HTTP (201, 404, 400)
 * ✅ Serialization JSON (automatique via Jackson)
 * 
 * CE QUI NE DOIT PAS ÊTRE ICI:
 * ❌ Logique métier (dans service)
 * ❌ Accès DB direct (dans repository)
 * ❌ Transactions (dans service avec @Transactional)
 * 
 * REST API DESIGN:
 * - GET /tenants: liste avec filtres + pagination
 * - GET /tenants/{id}: détail
 * - POST /tenants: création
 * - PUT /tenants/{id}: mise à jour
 * - DELETE /tenants/{id}: soft delete
 * - GET /tenants/stats: statistiques globales
 */
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;
    private final TenantStatsService tenantStatsService;

    /**
     * F1.1: Créer un nouveau tenant.
     * 
     * POST /api/v1/tenants
     * 
     * VALIDATION:
     * - @Valid: déclenche Bean Validation sur CreateTenantRequest
     * - @NotBlank, @Pattern dans DTO
     * 
     * HTTP STATUS:
     * - 201 Created: succès
     * - 400 Bad Request: validation fail
     * - 409 Conflict: subdomain existe déjà
     */
    @PostMapping
    public ResponseEntity<TenantResponse> createTenant(
        @Valid @RequestBody CreateTenantRequest request
    ) {
        TenantResponse response = tenantService.createTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * F1.2: Rechercher tenants avec filtres dynamiques.
     * 
     * GET /api/v1/tenants?name=acme&status=ACTIVE&page=0&size=20
     * 
     * PAGINATION:
     * - @PageableDefault: valeurs par défaut si pas spécifié
     * - Pageable: construit depuis query params (page, size, sort)
     * - Page<T>: contient data + metadata (totalElements, totalPages)
     * 
     * EXEMPLE RESPONSE:
     * ```json
     * {
     *   "content": [...],
     *   "totalElements": 150,
     *   "totalPages": 8,
     *   "size": 20,
     *   "number": 0
     * }
     * ```
     */
    @GetMapping
    public ResponseEntity<Page<TenantResponse>> searchTenants(
        @ModelAttribute TenantSearchCriteria criteria,
        @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        Page<TenantResponse> tenants = tenantService.searchTenants(criteria, pageable);
        return ResponseEntity.ok(tenants);
    }

    /**
     * GET /api/v1/tenants/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<TenantResponse> getTenantById(@PathVariable UUID id) {
        TenantResponse tenant = tenantService.getTenantById(id);
        return ResponseEntity.ok(tenant);
    }

    /**
     * F1.4: Mise à jour tenant.
     * 
     * PUT /api/v1/tenants/{id}
     * 
     * OPTIMISTIC LOCKING:
     * - Frontend envoie version dans request
     * - Si version DB ≠ version request: 409 Conflict
     * - Frontend doit refresh et retry
     */
    @PutMapping("/{id}")
    public ResponseEntity<TenantResponse> updateTenant(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateTenantRequest request
    ) {
        TenantResponse updated = tenantService.updateTenant(id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * F1.5: Soft delete tenant.
     * 
     * DELETE /api/v1/tenants/{id}
     * 
     * HTTP STATUS:
     * - 204 No Content: succès (pas de body)
     * - 404 Not Found: tenant inexistant
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTenant(@PathVariable UUID id) {
        tenantService.deleteTenant(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * F1.3: Statistiques globales dashboard admin.
     * 
     * GET /api/v1/tenants/stats
     * 
     * CACHE:
     * - Résultat caché 10 minutes
     * - Native Query complexe avec CTEs, window functions
     */
    @GetMapping("/stats")
    public ResponseEntity<TenantStatsResponse> getGlobalStats() {
        TenantStatsResponse stats = tenantStatsService.getGlobalStats();
        return ResponseEntity.ok(stats);
    }
}
