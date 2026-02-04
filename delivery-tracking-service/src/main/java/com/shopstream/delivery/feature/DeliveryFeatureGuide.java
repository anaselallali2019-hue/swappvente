package com.shopstream.delivery.feature;

import java.time.Instant;
import java.util.UUID;

public class DeliveryFeatureGuide {

    // ---------- F9.1 Streaming GPS positions ----------
    /**
     * F9.1 Streaming GPS positions.
     *
     * Technology/approach:
     * - WebSocket (STOMP) to receive positions.
     * - Kafka to stream positions.
     * - TimescaleDB for time-series storage.
     *
     * Why this approach:
     * - TimescaleDB is optimized for high-frequency time-series.
     *
     * Best practices:
     * - Use hypertables and time indexes.
     *
     * Advanced patterns:
     * - Stream ingest pipeline (WebSocket -> Kafka -> TSDB).
     *
     * Pitfalls to avoid:
     * - Writing into plain table without hypertable.
     */
    public void handlePosition(GpsPosition position) { }

    // ---------- F9.2 ETA calculation ----------
    /**
     * F9.2 ETA calculation.
     *
     * Technology/approach:
     * - Google Maps Distance Matrix API.
     * - Kafka Streams to recompute ETA.
     * - Redis cache for eta:{order_id}.
     *
     * Why this approach:
     * - Traffic-aware ETA from external API.
     *
     * Best practices:
     * - Batch API calls and cache results.
     *
     * Advanced patterns:
     * - Stream join of positions and orders.
     *
     * Pitfalls to avoid:
     * - Calling API for every position update.
     */
    public void recomputeEta(UUID orderId) { }

    // ---------- F9.3 Geofencing ----------
    /**
     * F9.3 Geofencing (500m zone).
     *
     * Technology/approach:
     * - PostGIS ST_DWithin for distance queries.
     * - Kafka Streams for real-time detection.
     *
     * Why this approach:
     * - Spatial indexes in PostGIS are fast.
     *
     * Best practices:
     * - GIST index on delivery_location.
     *
     * Advanced patterns:
     * - Geo-fencing with stream processing.
     *
     * Pitfalls to avoid:
     * - Distance calculations in application code.
     */
    public void detectApproaching(GpsPosition position) { }

    // ---------- Placeholder types ----------
    public record GpsPosition(UUID driverId, UUID orderId, double lat, double lon, Instant time) { }
}
