package com.shopstream.gateway.feature;

public class GatewayFeatureGuide {

    // ---------- F11.1 API Gateway ----------
    /**
     * F11.1 API Gateway with Spring Cloud Gateway.
     *
     * Technology/approach:
     * - Spring Cloud Gateway (not Zuul).
     * - Resilience4j circuit breaker.
     * - Redis distributed rate limiting.
     *
     * Why this approach:
     * - SCG is reactive and maintained.
     *
     * Best practices:
     * - Per-route circuit breakers.
     * - Centralized rate limiting with Redis.
     *
     * Advanced patterns:
     * - API Gateway + resilience filters.
     *
     * Pitfalls to avoid:
     * - Local memory rate limiting (not shared).
     */
    public void configureRoutes() { }

    // ---------- F11.2 JWT Authentication ----------
    /**
     * F11.2 JWT Authentication.
     *
     * Technology/approach:
     * - Spring Security OAuth2 Resource Server.
     * - Keycloak as authorization server.
     * - JWT validation at gateway.
     *
     * Why this approach:
     * - Centralized auth at edge.
     *
     * Best practices:
     * - Use custom claims (tenant_id, roles, permissions).
     *
     * Advanced patterns:
     * - Claim mapping for multi-tenancy.
     *
     * Pitfalls to avoid:
     * - Skipping validation for internal routes.
     */
    public void validateJwt() { }

    // ---------- F11.3 Distributed rate limiting ----------
    /**
     * F11.3 Distributed rate limiting.
     *
     * Technology/approach:
     * - Redis Sorted Sets + Lua script for atomic sliding window.
     *
     * Why this approach:
     * - Single atomic script avoids races.
     *
     * Best practices:
     * - Key by tenant + user.
     *
     * Advanced patterns:
     * - Lua-based atomic rate limiter.
     *
     * Pitfalls to avoid:
     * - Multi-step Redis calls without Lua.
     */
    public void rateLimit() { }
}
