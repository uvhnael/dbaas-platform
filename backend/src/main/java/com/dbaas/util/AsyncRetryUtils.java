package com.dbaas.util;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Utility class for async retry operations.
 * Replaces blocking Thread.sleep() with non-blocking async patterns.
 * 
 * <p>
 * Now a Spring bean with proper lifecycle management for graceful shutdown.
 * </p>
 */
@Slf4j
@Component
public class AsyncRetryUtils {

    private final ScheduledExecutorService scheduler;

    public AsyncRetryUtils() {
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "async-retry-scheduler");
            t.setDaemon(true);
            return t;
        });
        log.debug("AsyncRetryUtils scheduler initialized");
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down AsyncRetryUtils scheduler...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("AsyncRetryUtils scheduler did not terminate gracefully, forcing shutdown");
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
        log.info("AsyncRetryUtils scheduler shutdown complete");
    }

    /**
     * Execute an action with async retries using CompletableFuture.
     * Non-blocking alternative to Thread.sleep() in retry loops.
     *
     * @param actionName Name for logging
     * @param action     The action to execute
     * @param maxRetries Maximum retry attempts
     * @param delay      Delay between retries
     * @param executor   Executor for async execution
     * @return CompletableFuture that completes when action succeeds or all retries
     *         exhausted
     */
    public CompletableFuture<Void> executeWithRetryAsync(
            String actionName,
            Runnable action,
            int maxRetries,
            Duration delay,
            Executor executor) {

        return retryAsync(actionName, () -> {
            action.run();
            return null;
        }, maxRetries, delay, executor, 1);
    }

    /**
     * Execute a supplier with async retries.
     *
     * @param <T>        Return type
     * @param actionName Name for logging
     * @param supplier   The supplier to execute
     * @param maxRetries Maximum retry attempts
     * @param delay      Delay between retries
     * @param executor   Executor for async execution
     * @return CompletableFuture with the result
     */
    public <T> CompletableFuture<T> executeWithRetryAsync(
            String actionName,
            Supplier<T> supplier,
            int maxRetries,
            Duration delay,
            Executor executor) {

        return retryAsync(actionName, supplier, maxRetries, delay, executor, 1);
    }

    private <T> CompletableFuture<T> retryAsync(
            String actionName,
            Supplier<T> supplier,
            int maxRetries,
            Duration delay,
            Executor executor,
            int currentAttempt) {

        return CompletableFuture.supplyAsync(supplier, executor)
                .exceptionallyCompose(ex -> {
                    if (currentAttempt >= maxRetries) {
                        log.error("{} failed after {} attempts: {}",
                                actionName, maxRetries, ex.getMessage());
                        return CompletableFuture.failedFuture(ex);
                    }

                    log.warn("{} failed on attempt {}/{}: {}. Retrying in {}ms...",
                            actionName, currentAttempt, maxRetries,
                            ex.getMessage(), delay.toMillis());

                    CompletableFuture<T> delayedRetry = new CompletableFuture<>();

                    scheduler.schedule(() -> {
                        retryAsync(actionName, supplier, maxRetries, delay, executor, currentAttempt + 1)
                                .whenComplete((result, error) -> {
                                    if (error != null) {
                                        delayedRetry.completeExceptionally(error);
                                    } else {
                                        delayedRetry.complete(result);
                                    }
                                });
                    }, delay.toMillis(), TimeUnit.MILLISECONDS);

                    return delayedRetry;
                });
    }

    /**
     * Create a delayed CompletableFuture without blocking.
     * Use this instead of Thread.sleep() for simple delays.
     *
     * @param delay Duration to wait
     * @return CompletableFuture that completes after the delay
     */
    public CompletableFuture<Void> delay(Duration delay) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        scheduler.schedule(() -> future.complete(null), delay.toMillis(), TimeUnit.MILLISECONDS);
        return future;
    }

    /**
     * Create a delayed CompletableFuture with a value.
     *
     * @param <T>   Value type
     * @param value Value to return after delay
     * @param delay Duration to wait
     * @return CompletableFuture that completes with value after the delay
     */
    public <T> CompletableFuture<T> delayedValue(T value, Duration delay) {
        CompletableFuture<T> future = new CompletableFuture<>();
        scheduler.schedule(() -> future.complete(value), delay.toMillis(), TimeUnit.MILLISECONDS);
        return future;
    }

    /**
     * Execute action after a delay, non-blocking.
     *
     * @param action   Action to execute
     * @param delay    Duration to wait before execution
     * @param executor Executor for running the action
     * @return CompletableFuture that completes after action executes
     */
    public CompletableFuture<Void> executeAfterDelay(
            Runnable action,
            Duration delay,
            Executor executor) {

        return delay(delay)
                .thenRunAsync(action, executor);
    }
}
