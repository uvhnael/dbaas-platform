package com.dbaas.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Health indicator for Redis cache connectivity.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Health health() {
        try {
            RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
            if (connectionFactory == null) {
                return Health.down()
                        .withDetail("error", "No Redis connection factory configured")
                        .build();
            }

            // Test connection by pinging
            String result = connectionFactory.getConnection().ping();
            
            if ("PONG".equals(result)) {
                return Health.up()
                        .withDetail("connection", "established")
                        .withDetail("ping", result)
                        .build();
            } else {
                return Health.down()
                        .withDetail("error", "Unexpected ping response: " + result)
                        .build();
            }
            
        } catch (Exception e) {
            log.warn("Redis health check failed: {}", e.getMessage());
            // Redis is optional, mark as OUT_OF_SERVICE instead of DOWN
            return Health.outOfService()
                    .withDetail("error", e.getMessage())
                    .withDetail("note", "Cache is disabled, application will function without caching")
                    .build();
        }
    }
}
