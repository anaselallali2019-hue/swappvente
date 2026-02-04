import java.util.List;
import java.util.UUID;

/**
 * Recommendation Service blueprint.
 */
public class RecommendationServiceBlueprint {

    // ---------- F7.1 Collaborative filtering ----------
    /**
     * F7.1 Collaborative filtering.
     *
     * Tech/approach:
     * - Apache Mahout or Spark MLlib.
     * - User-User collaborative filtering.
     * - ALS (Alternating Least Squares) for embeddings.
     *
     * Data pipeline:
     * 1) Kafka Streams reads order.completed.
     * 2) Build user-item matrix (implicit ratings).
     * 3) Train ALS offline nightly.
     * 4) Store embeddings in Redis.
     *
     * Why this and not another:
     * - ALS scales well for sparse matrices.
     *
     * Best practices:
     * - Validate training data quality and remove outliers.
     * - Schedule retraining to keep model fresh.
     *
     * Advanced patterns:
     * - Offline training + online serving pattern.
     *
     * Pitfalls to avoid:
     * - Training online per request (too slow).
     */
    public void runNightlyAlsTraining() { }

    /**
     * Alternative (no ML):
     * - Frequently Bought Together via SQL co-occurrence query.
     */
    public List<UUID> frequentlyBoughtTogether(UUID productId) {
        return List.of();
    }

    // ---------- F7.2 Real-time personalization ----------
    /**
     * F7.2 Real-time personalization.
     *
     * Tech/approach:
     * - Kafka Streams for behavioral events.
     * - Redis Sorted Sets for ranking.
     * - Events: product.viewed, product.added_to_cart, product.purchased.
     *
     * Why this and not another:
     * - Streaming provides near-real-time personalization.
     *
     * Best practices:
     * - Session windows 30 min to capture short-term intent.
     * - Use scoring weights: view=1, add_to_cart=3, purchase=10.
     *
     * Advanced patterns:
     * - Stateful stream aggregation with session windows.
     *
     * Pitfalls to avoid:
     * - Long windows that ignore fresh behavior.
     */
    public void personalizeHomePage(UUID userId) { }

    // ---------- Schema (blueprint) ----------
    /**
     * user_interactions (
     *   id UUID PRIMARY KEY,
     *   user_id UUID,
     *   product_id UUID,
     *   interaction_type VARCHAR(50),
     *   score INT,
     *   created_at TIMESTAMP
     * );
     *
     * product_similarities (
     *   product_id UUID,
     *   similar_product_id UUID,
     *   similarity_score DECIMAL(5,4),
     *   PRIMARY KEY (product_id, similar_product_id)
     * );
     */
}
