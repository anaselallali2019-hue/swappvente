package com.shopstream.notification.feature;

import java.util.UUID;

public class NotificationFeatureGuide {

    // ---------- F8.1 Multi-channel notifications ----------
    /**
     * F8.1 Multi-channel notifications.
     *
     * Technology/approach:
     * - Strategy Pattern per channel (Email, SMS, Push, WebSocket).
     * - Thymeleaf for email templates.
     * - Twilio for SMS, FCM for push, STOMP for WebSocket.
     * - Kafka consumer groups per channel.
     *
     * Why this approach:
     * - Channels evolve independently.
     *
     * Best practices:
     * - Retry with DLQ after failures.
     * - Isolate consumer groups per channel.
     *
     * Advanced patterns:
     * - Strategy + provider adapters.
     *
     * Pitfalls to avoid:
     * - Single consumer group for all channels.
     */
    public void sendNotification(NotificationCommand cmd) { }

    // ---------- F8.2 Rate limiting ----------
    /**
     * F8.2 Rate limiting (10 emails/day per user).
     *
     * Technology/approach:
     * - Redis sliding window or token bucket.
     *
     * Why this approach:
     * - Distributed atomic counters for multi-instance.
     *
     * Best practices:
     * - Use per-user and per-channel keys.
     *
     * Advanced patterns:
     * - Distributed rate limiter with Redis.
     *
     * Pitfalls to avoid:
     * - Non-atomic updates across instances.
     */
    public void enforceRateLimit(UUID userId, String channel) { }

    // ---------- F8.3 Notification preferences ----------
    /**
     * F8.3 Notification preferences.
     *
     * Technology/approach:
     * - notification_preferences table with flags.
     *
     * Why this approach:
     * - Simple and fast preference checks.
     *
     * Best practices:
     * - Check preferences before dispatch.
     *
     * Advanced patterns:
     * - Preference-aware routing pipeline.
     *
     * Pitfalls to avoid:
     * - Ignoring user preferences.
     */
    public void applyPreferences(UUID userId, String channel) { }

    // ---------- Placeholder types ----------
    public record NotificationCommand(UUID userId, String channel, String template) { }
}
