package com.shopstream.order.feature;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class OrderFeatureGuide {

    // ---------- F4.1 Create order with event sourcing ----------
    /**
     * F4.1 Create order using event sourcing.
     *
     * Technology/approach:
     * - Event Sourcing (all changes are events).
     * - Kafka compacted topic as event store.
     * - Snapshots every 50 events.
     * - CQRS: write model (events) vs read model (PostgreSQL).
     * - Avro schemas for event types.
     *
     * Why this approach:
     * - Full audit trail and temporal queries.
     * - Easy replay for analytics and debugging.
     *
     * Best practices:
     * - Use event version per aggregate.
     * - Store snapshots for faster rehydration.
     *
     * Advanced patterns:
     * - Event sourcing + CQRS separation.
     *
     * Pitfalls to avoid:
     * - Mutating state without emitting event.
     * - No snapshots for long-lived aggregates.
     */
    public OrderAggregate createOrder(CreateOrderCommand cmd) {
        return new OrderAggregate();
    }

    // ---------- F4.2 CQRS query side ----------
    /**
     * F4.2 Query side for orders with filters.
     *
     * Technology/approach:
     * - PostgreSQL read model (denormalized).
     * - Kafka consumer projects order-events into read model.
     * - Criteria API + Specification for dynamic filters.
     *
     * Why this approach:
     * - Read model optimized for queries.
     * - Criteria supports optional filters.
     *
     * Best practices:
     * - Idempotent projection with last_event_version.
     *
     * Advanced patterns:
     * - Event projection with idempotent consumer.
     *
     * Pitfalls to avoid:
     * - Applying out-of-order events.
     */
    public OrderPage listOrders(OrderSearchFilter filter, Pageable pageable) {
        return new OrderPage(List.of(), 0, 0);
    }

    // ---------- F4.3 Saga orchestration ----------
    /**
     * F4.3 Saga orchestration for order workflow.
     *
     * Technology/approach:
     * - Saga Orchestration Pattern.
     * - State Machine for steps.
     * - Compensation logic for rollback.
     *
     * Why this approach:
     * - Central control and visibility.
     *
     * Best practices:
     * - Persist saga state (saga_instances + saga_steps).
     *
     * Advanced patterns:
     * - Orchestrated saga with compensations.
     *
     * Pitfalls to avoid:
     * - Missing compensation for partial failures.
     */
    public void startSaga(UUID orderId) { }

    // ---------- F4.4 Advanced analytics ----------
    /**
     * F4.4 Advanced analytics (revenue, top products).
     *
     * Technology/approach:
     * - Native SQL with CTE and window functions.
     * - Materialized view refreshed on schedule.
     * - Kafka Streams for real-time metrics.
     *
     * Why this approach:
     * - SQL window functions are optimal for analytics.
     *
     * Best practices:
     * - Refresh materialized views to avoid runtime load.
     *
     * Advanced patterns:
     * - Materialized views + streaming metrics.
     *
     * Pitfalls to avoid:
     * - Heavy aggregates on OLTP tables per request.
     */
    public List<MonthlyRevenueRow> monthlyRevenue(UUID tenantId) {
        return List.of();
    }

    // ---------- F4.5 Fraud detection ----------
    /**
     * F4.5 Fraud detection (real time).
     *
     * Technology/approach:
     * - Kafka Streams with state stores.
     * - Windowed aggregation (hopping 1h).
     * - Rules table fraud_rules for configurable rules.
     *
     * Why this approach:
     * - Real-time detection with stateful processing.
     *
     * Best practices:
     * - Emit order.fraud-suspected event.
     *
     * Advanced patterns:
     * - Stateful windowed processing.
     *
     * Pitfalls to avoid:
     * - Hard-coded rules in code.
     */
    public void configureFraudStreams() { }

    // ---------- Placeholder types ----------
    public record CreateOrderCommand(UUID tenantId, UUID customerId, List<OrderItemInput> items) { }
    public record OrderItemInput(UUID productId, int quantity, BigDecimal price) { }
    public record OrderPage(List<OrderReadModel> items, int page, int size) { }
    public record OrderSearchFilter(UUID customerId, Instant from, Instant to, String status) { }
    public record MonthlyRevenueRow(Instant month, BigDecimal revenue) { }

    public static final class OrderAggregate { }
    public static final class OrderReadModel { }
    public interface Pageable { }
}
