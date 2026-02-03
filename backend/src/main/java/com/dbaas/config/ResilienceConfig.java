package com.dbaas.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j configuration for retry and circuit breaker patterns.
 * Provides fault tolerance for external service calls.
 */
@Configuration
@Slf4j
public class ResilienceConfig {

    // Circuit Breaker names
    public static final String DOCKER_CB = "dockerCircuitBreaker";
    public static final String ORCHESTRATOR_CB = "orchestratorCircuitBreaker";
    public static final String PROXYSQL_CB = "proxysqlCircuitBreaker";
    public static final String DATABASE_CB = "databaseCircuitBreaker";

    // Retry names
    public static final String DOCKER_RETRY = "dockerRetry";
    public static final String MYSQL_RETRY = "mysqlRetry";
    public static final String ORCHESTRATOR_RETRY = "orchestratorRetry";

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        // Default config for most services
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50) // Open circuit when 50% of calls fail
                .slowCallRateThreshold(80) // Consider slow if >80% are slow
                .slowCallDurationThreshold(Duration.ofSeconds(10))
                .waitDurationInOpenState(Duration.ofSeconds(30)) // Wait before half-open
                .permittedNumberOfCallsInHalfOpenState(3)
                .minimumNumberOfCalls(5) // Minimum calls before calculating failure rate
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .build();

        // Docker operations - more tolerant (Docker can be slow)
        CircuitBreakerConfig dockerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(60)
                .slowCallDurationThreshold(Duration.ofSeconds(30))
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(2)
                .minimumNumberOfCalls(3)
                .slidingWindowSize(10)
                .build();

        // Orchestrator - external service, more sensitive
        CircuitBreakerConfig orchestratorConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(40)
                .slowCallDurationThreshold(Duration.ofSeconds(5))
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .permittedNumberOfCallsInHalfOpenState(2)
                .minimumNumberOfCalls(3)
                .slidingWindowSize(10)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(defaultConfig);
        registry.circuitBreaker(DOCKER_CB, dockerConfig);
        registry.circuitBreaker(ORCHESTRATOR_CB, orchestratorConfig);
        registry.circuitBreaker(PROXYSQL_CB, defaultConfig);
        registry.circuitBreaker(DATABASE_CB, defaultConfig);

        // Add event listeners for monitoring
        registry.getAllCircuitBreakers().forEach(cb -> {
            cb.getEventPublisher()
                    .onStateTransition(event -> 
                            log.warn("CircuitBreaker '{}' state changed: {} -> {}", 
                                    cb.getName(), 
                                    event.getStateTransition().getFromState(),
                                    event.getStateTransition().getToState()))
                    .onError(event -> 
                            log.debug("CircuitBreaker '{}' error: {}", 
                                    cb.getName(), event.getThrowable().getMessage()));
        });

        return registry;
    }

    @Bean
    public RetryRegistry retryRegistry() {
        // Docker retry - longer intervals, fewer attempts
        RetryConfig dockerRetryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(5))
                .retryExceptions(RuntimeException.class)
                .build();

        // MySQL retry - shorter intervals, more attempts (for connection issues)
        RetryConfig mysqlRetryConfig = RetryConfig.custom()
                .maxAttempts(5)
                .waitDuration(Duration.ofSeconds(2))
                .retryExceptions(RuntimeException.class)
                .build();

        // Orchestrator retry
        RetryConfig orchestratorRetryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(3))
                .retryExceptions(RuntimeException.class)
                .build();

        RetryRegistry registry = RetryRegistry.of(dockerRetryConfig);
        registry.retry(DOCKER_RETRY, dockerRetryConfig);
        registry.retry(MYSQL_RETRY, mysqlRetryConfig);
        registry.retry(ORCHESTRATOR_RETRY, orchestratorRetryConfig);

        // Add event listeners
        registry.getAllRetries().forEach(retry -> {
            retry.getEventPublisher()
                    .onRetry(event -> 
                            log.info("Retry '{}' attempt #{}", 
                                    retry.getName(), event.getNumberOfRetryAttempts()))
                    .onError(event -> 
                            log.warn("Retry '{}' failed after {} attempts", 
                                    retry.getName(), event.getNumberOfRetryAttempts()));
        });

        return registry;
    }

    /**
     * Get Docker circuit breaker.
     */
    @Bean
    public CircuitBreaker dockerCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker(DOCKER_CB);
    }

    /**
     * Get Docker retry.
     */
    @Bean
    public Retry dockerRetry(RetryRegistry registry) {
        return registry.retry(DOCKER_RETRY);
    }

    /**
     * Get MySQL retry.
     */
    @Bean
    public Retry mysqlRetry(RetryRegistry registry) {
        return registry.retry(MYSQL_RETRY);
    }
}
