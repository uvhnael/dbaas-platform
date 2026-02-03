package com.dbaas.exception;

/**
 * Exception thrown when user profile update fails.
 */
public class ProfileUpdateException extends RuntimeException {

    private final String userId;

    public ProfileUpdateException(String userId, String message) {
        super(message);
        this.userId = userId;
    }

    public ProfileUpdateException(String userId, String message, Throwable cause) {
        super(message, cause);
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }
}
