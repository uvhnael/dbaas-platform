package com.dbaas.exception;

/**
 * Exception thrown when attempting to register with an existing username.
 */
public class UsernameAlreadyExistsException extends RuntimeException {

    private final String username;

    public UsernameAlreadyExistsException(String username) {
        super("Username already exists: " + username);
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
