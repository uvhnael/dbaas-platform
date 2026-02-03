package com.dbaas.exception;

/**
 * Exception thrown when rate limit is exceeded.
 */
public class RateLimitExceededException extends RuntimeException {

    private final String plan;
    private final int limit;
    private final long retryAfterSeconds;

    public RateLimitExceededException(String plan, int limit, long retryAfterSeconds) {
        super(String.format("Rate limit exceeded for plan '%s'. Limit: %d requests. Retry after %d seconds.",
                plan, limit, retryAfterSeconds));
        this.plan = plan;
        this.limit = limit;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getPlan() {
        return plan;
    }

    public int getLimit() {
        return limit;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
