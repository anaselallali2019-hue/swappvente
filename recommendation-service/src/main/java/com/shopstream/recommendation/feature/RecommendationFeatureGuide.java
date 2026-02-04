package com.shopstream.recommendation.feature;

import java.util.List;
import java.util.UUID;

public class RecommendationFeatureGuide {

    // ---------- F7.1 Collaborative filtering ----------
    /**
     * F7.1 Collaborative filtering.
     *
     * Technology/approach:
     * - Apache Mahout or Spark MLlib.
     * - User-User collaborative filtering.
     * - ALS (Alternating Least Squares).
     *
     * Why this approach:
     * - ALS scales for sparse user-item matrices.
     *
     * Best practices:
     * - Train offline nightly with fresh data.
     * - Store embeddings in Redis for fast serving.
     *
     * Advanced patterns:
     * - Offline training + online serving.
     *
     * Pitfalls to avoid:
     * - Training per request (too slow).
     */
    public void runNightlyTraining() { }

    // ---------- F7.2 Real-time personalization ----------
    /**
     * F7.2 Real-time personalization.
     *
     * Technology/approach:
     * - Kafka Streams for behavioral events.
     * - Redis Sorted Sets for ranking.
     * - Event types: product.viewed, product.added_to_cart, product.purchased.
     *
     * Why this approach:
     * - Streaming keeps recommendations fresh.
     *
     * Best practices:
     * - Session window 30 min for intent.
     * - Weight scores: view=1, cart=3, purchase=10.
     *
     * Advanced patterns:
     * - Stateful aggregation with session windows.
     *
     * Pitfalls to avoid:
     * - Long windows that ignore recent behavior.
     */
    public void personalizeHome(UUID userId) { }

    // ---------- Alternative simple SQL ----------
    /**
     * Alternative if no ML:
     * - Frequently bought together via SQL co-occurrence.
     */
    public List<UUID> frequentlyBoughtTogether(UUID productId) {
        return List.of();
    }
}
