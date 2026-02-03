package com.dbaas.exception;

/**
 * Exception thrown when user registration fails.
 */
public class RegistrationException extends RuntimeException {

    private final String username;

    public RegistrationException(String username, String message) {
        super(message);
        this.username = username;
    }

    public RegistrationException(String username, String message, Throwable cause) {
        super(message, cause);
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
