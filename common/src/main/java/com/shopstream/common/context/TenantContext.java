package com.shopstream.common.context;

import lombok.experimental.UtilityClass;

import java.util.UUID;

/**
 * Thread-safe context pour stocker le tenant_id de la requête courante.
 * 
 * POURQUOI ThreadLocal:
 * - Chaque requête HTTP = 1 thread (Tomcat thread pool)
 * - Évite de passer tenant_id en paramètre partout
 * - Accessible depuis n'importe quelle couche (controller -> service -> repository)
 * 
 * PATTERN: Context Object Pattern
 * 
 * UTILISATION:
 * 1. Filter HTTP extrait tenant_id du JWT ou header X-Tenant-ID
 * 2. TenantContext.setCurrentTenant(tenantId)
 * 3. Tout le code métier accède via TenantContext.getCurrentTenant()
 * 4. Filter cleanup dans finally block
 * 
 * ALTERNATIVE SI VIRTUAL THREADS (Java 21):
 * - Utiliser ScopedValue au lieu de ThreadLocal (immutable, meilleure perf)
 */
@UtilityClass
public class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new InheritableThreadLocal<>();

    public static void setCurrentTenant(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getCurrentTenant() {
        UUID tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new IllegalStateException(
                "Tenant context not set. " +
                "Ensure TenantFilter is properly configured and JWT contains tenant_id claim."
            );
        }
        return tenantId;
    }

    public static UUID getCurrentTenantOrNull() {
        return CURRENT_TENANT.get();
    }

    public static boolean isSet() {
        return CURRENT_TENANT.get() != null;
    }

    /**
     * CRITICAL: Always call in finally block to avoid memory leak.
     * 
     * POURQUOI:
     * - Tomcat réutilise threads (thread pool)
     * - Si pas cleanup, requête suivante sur même thread verra ancien tenant_id
     * - Memory leak si tenant_id jamais supprimé
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
