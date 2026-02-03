package com.dbaas.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiting configuration using Bucket4j.
 * Provides different rate limits for various API endpoints.
 */
@Configuration
public class RateLimitingConfig {

    /**
     * Rate limit plans for different operations.
     */
    public enum RateLimitPlan {
        // Create cluster: 5 requests per minute (expensive operation)
        CREATE_CLUSTER(5, Duration.ofMinutes(1)),

        // Scale operations: 10 per minute
        SCALE_CLUSTER(10, Duration.ofMinutes(1)),

        // Delete operations: 10 per minute
        DELETE_CLUSTER(10, Duration.ofMinutes(1)),

        // Authentication: 20 per minute (prevent brute force)
        AUTH(20, Duration.ofMinutes(1)),

        // General API: 100 requests per minute
        GENERAL_API(100, Duration.ofMinutes(1)),

        // Metrics/Monitoring: 200 per minute (frequent polling)
        METRICS(200, Duration.ofMinutes(1));

        private final int capacity;
        private final Duration duration;

        RateLimitPlan(int capacity, Duration duration) {
            this.capacity = capacity;
            this.duration = duration;
        }

        public int getCapacity() {
            return capacity;
        }

        public Duration getDuration() {
            return duration;
        }
    }

    @Component
    public static class RateLimiter {
        /**
         * Caffeine cache with TTL to prevent memory leaks.
         * Buckets expire after 1 hour of inactivity.
         * Maximum 10,000 buckets to prevent OOM.
         */
        private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(1, TimeUnit.HOURS)
                .build();

        /**
         * Resolve bucket for a specific key and plan.
         */
        public Bucket resolveBucket(String key, RateLimitPlan plan) {
            String bucketKey = plan.name() + ":" + key;
            return buckets.get(bucketKey, k -> createBucket(plan));
        }

        /**
         * Try to consume a token from the bucket.
         * 
         * @return true if allowed, false if rate limited
         */
        public boolean tryConsume(String key, RateLimitPlan plan) {
            Bucket bucket = resolveBucket(key, plan);
            return bucket.tryConsume(1);
        }

        /**
         * Get remaining tokens for a key.
         */
        public long getAvailableTokens(String key, RateLimitPlan plan) {
            Bucket bucket = resolveBucket(key, plan);
            return bucket.getAvailableTokens();
        }

        private Bucket createBucket(RateLimitPlan plan) {
            // Using new Bucket4j 8.x API (simple method)
            Bandwidth limit = Bandwidth.simple(
                    plan.getCapacity(),
                    plan.getDuration());
            return Bucket.builder()
                    .addLimit(limit)
                    .build();
        }

        /**
         * Clear bucket for a key (useful for testing).
         */
        public void clearBucket(String key, RateLimitPlan plan) {
            String bucketKey = plan.name() + ":" + key;
            buckets.invalidate(bucketKey);
        }

        /**
         * Get current cache size (for monitoring).
         */
        public long getCacheSize() {
            return buckets.estimatedSize();
        }
    }
}
