package com.shopstream.pricing.feature;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class PricingFeatureGuide {

    // ---------- F6.1 Pricing rules (Drools) ----------
    /**
     * F6.1 Pricing with complex rules.
     *
     * Technology/approach:
     * - Drools Rule Engine (KIE Workbench).
     * - Strategy Pattern for pricing strategies.
     * - Chain of Responsibility for rule application order.
     *
     * Why this approach:
     * - Business can update rules without code changes.
     *
     * Best practices:
     * - Cache active rules per tenant in Redis.
     * - Version rules and keep audit history.
     *
     * Advanced patterns:
     * - Rules engine + strategy + chain of responsibility.
     *
     * Pitfalls to avoid:
     * - Hard-coded rules in codebase.
     */
    public PriceResult calculatePrice(PricingContext context) {
        return new PriceResult(context.basePrice());
    }

    // ---------- F6.2 Dynamic pricing with ML ----------
    /**
     * F6.2 Dynamic pricing (optional ML).
     *
     * Technology/approach:
     * - Python microservice (Flask) with scikit-learn.
     * - Kafka Streams for real-time features.
     * - gRPC for Java <-> Python.
     * - Redis cache TTL 10 minutes.
     *
     * Why this approach:
     * - ML model evolves independently from Java services.
     *
     * Best practices:
     * - Cache predictions to reduce ML calls.
     *
     * Advanced patterns:
     * - Streaming features + gRPC inference.
     *
     * Pitfalls to avoid:
     * - Calling ML service per request without cache.
     */
    public PriceResult predictDynamicPrice(PricingContext context) {
        return new PriceResult(context.basePrice());
    }

    // ---------- F6.3 A/B testing ----------
    /**
     * F6.3 A/B testing for prices.
     *
     * Technology/approach:
     * - Feature flags (Unleash or custom).
     * - Variant assignment by customer_id hash.
     * - Kafka Streams for conversion metrics.
     *
     * Why this approach:
     * - Deterministic assignment for consistent UX.
     *
     * Best practices:
     * - Log assignment in ab_test_assignments.
     * - Track events in ab_test_events.
     *
     * Advanced patterns:
     * - Deterministic bucketing for A/B tests.
     *
     * Pitfalls to avoid:
     * - Reassigning customers between variants.
     */
    public BigDecimal assignVariantPrice(UUID customerId, BigDecimal basePrice) {
        int variant = Math.abs(customerId.hashCode()) % 2;
        return variant == 0 ? basePrice : basePrice.multiply(BigDecimal.valueOf(0.9));
    }

    // ---------- Placeholder types ----------
    public record PricingContext(UUID tenantId, UUID productId, BigDecimal basePrice, Instant now) { }
    public record PriceResult(BigDecimal finalPrice) { }
}
