package com.shopstream.tenant.domain.factory;

import com.shopstream.tenant.application.dto.CreateTenantRequest;
import com.shopstream.tenant.domain.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Factory pour créer Tenant avec configuration initiale.
 * 
 * PATTERN: Factory Pattern
 * 
 * POURQUOI Factory (et pas new Tenant() partout):
 * ✅ Centralise logique création complexe
 * ✅ Configuration différente selon tier (Free vs Pro)
 * ✅ Évolution facile (nouveau tier = update factory)
 * ✅ Testable (mock factory dans tests)
 * 
 * ALTERNATIVE:
 * - Builder Pattern: bien pour objets simples
 * - Factory mieux pour logique conditionnelle
 */
@Component
@RequiredArgsConstructor
public class TenantFactory {

    /**
     * Créer tenant avec configuration par défaut selon tier.
     * 
     * TEMPLATE METHOD PATTERN:
     * - create() est le template
     * - createForFreeTier(), createForProTier() sont les variations
     */
    public Tenant createTenant(CreateTenantRequest request) {
        Tenant tenant = Tenant.builder()
            .name(request.getName())
            .subdomain(request.getSubdomain())
            .tier(request.getTier())
            .status(TenantStatus.ACTIVE)
            .settings(request.getSettings() != null ? request.getSettings() : getDefaultSettings())
            .build();

        // Créer subscription initiale
        TenantSubscription subscription = createInitialSubscription(tenant, request.getTier());
        tenant.addSubscription(subscription);

        // Créer admin user si email fourni
        if (request.getAdminEmail() != null) {
            TenantUser adminUser = TenantUser.builder()
                .email(request.getAdminEmail())
                .role(TenantUserRole.TENANT_ADMIN)
                .active(true)
                .build();
            tenant.addUser(adminUser);
        }

        return tenant;
    }

    /**
     * Créer subscription initiale selon tier.
     * 
     * STRATEGY PATTERN:
     * - Comportement différent selon tier
     * - Facilement extensible (nouveau tier)
     */
    private TenantSubscription createInitialSubscription(Tenant tenant, TenantTier tier) {
        return switch (tier) {
            case FREE -> TenantSubscription.builder()
                .tenant(tenant)
                .planName("Free Plan")
                .startDate(LocalDate.now())
                .endDate(null) // Unlimited
                .monthlyRevenue(BigDecimal.ZERO)
                .maxProducts(tier.getMaxProducts())
                .maxOrdersPerMonth(tier.getMaxOrdersPerMonth())
                .build();

            case PRO -> TenantSubscription.builder()
                .tenant(tenant)
                .planName("Pro Plan")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(1)) // Renew monthly
                .monthlyRevenue(new BigDecimal("49.99"))
                .maxProducts(tier.getMaxProducts())
                .maxOrdersPerMonth(tier.getMaxOrdersPerMonth())
                .build();

            case ENTERPRISE -> TenantSubscription.builder()
                .tenant(tenant)
                .planName("Enterprise Plan")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusYears(1)) // Annual
                .monthlyRevenue(new BigDecimal("499.99"))
                .maxProducts(tier.getMaxProducts())
                .maxOrdersPerMonth(tier.getMaxOrdersPerMonth())
                .build();
        };
    }

    /**
     * Configuration par défaut (JSON).
     */
    private String getDefaultSettings() {
        return """
            {
              "theme": {
                "primaryColor": "#007bff",
                "secondaryColor": "#6c757d"
              },
              "features": {
                "recommendations": false,
                "reviews": true,
                "wishlist": true
              },
              "emails": {
                "orderConfirmation": true,
                "marketing": false
              }
            }
            """;
    }
}
