import java.time.Instant;
import java.util.UUID;

/**
 * Analytics Service blueprint.
 */
public class AnalyticsServiceBlueprint {

    // ---------- F10.1 Real-time dashboards ----------
    /**
     * F10.1 Real-time dashboards.
     *
     * Tech/approach:
     * - Kafka Streams with windowed aggregations.
     * - Redis for metrics storage.
     * - Server-Sent Events (SSE) for push to frontend.
     *
     * Why this and not another:
     * - Streaming provides near-real-time metrics.
     *
     * Best practices:
     * - Use windowed aggregation and store in Redis for fast reads.
     *
     * Advanced patterns:
     * - Stream processing + cache-aside for dashboards.
     *
     * Pitfalls to avoid:
     * - Polling DB for every dashboard refresh.
     */
    public void streamAnalytics(UUID tenantId) { }

    // ---------- F10.2 OLAP queries ----------
    /**
     * F10.2 OLAP queries (drill-down/roll-up).
     *
     * Tech/approach:
     * - ClickHouse or Apache Druid as OLAP store.
     * - Kafka Connect sink to OLAP.
     * - Materialized views for pre-aggregation.
     *
     * Why this and not another:
     * - OLAP engines optimized for large analytics queries.
     *
     * Best practices:
     * - Partition by time and tenant for query speed.
     *
     * Advanced patterns:
     * - Materialized views in OLAP for pre-aggregation.
     *
     * Pitfalls to avoid:
     * - Running OLAP queries on OLTP database.
     */
    public void runOlapReport(UUID tenantId) { }

    // ---------- F10.3 Cohort analysis ----------
    /**
     * F10.3 Cohort analysis.
     *
     * Tech/approach:
     * - Native SQL with window functions.
     *
     * Why this and not another:
     * - Complex window logic is best expressed in SQL.
     *
     * Best practices:
     * - Use proper indexes on user and order dates.
     *
     * Advanced patterns:
     * - Window functions + cohort bucketing.
     *
     * Pitfalls to avoid:
     * - Recomputing cohorts without caching.
     */
    public void cohortAnalysis() { }

    // ---------- Schema (blueprint) ----------
    /**
     * ClickHouse order_facts table (example):
     * order_id UUID,
     * tenant_id UUID,
     * customer_id UUID,
     * order_date DateTime,
     * total_amount Decimal(12,2),
     * currency String,
     * status String,
     * product_category String,
     * payment_method String,
     * shipping_country String
     *
     * daily_metrics (
     *   tenant_id UUID,
     *   metric_date DATE,
     *   total_revenue DECIMAL(15,2),
     *   order_count INT,
     *   unique_customers INT,
     *   avg_order_value DECIMAL(10,2),
     *   top_product_id UUID,
     *   created_at TIMESTAMP DEFAULT NOW(),
     *   PRIMARY KEY (tenant_id, metric_date)
     * );
     */

    // ---------- Placeholder types ----------
    public record AnalyticsData(UUID tenantId, Instant windowStart) { }
}
