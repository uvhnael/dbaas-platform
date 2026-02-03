package com.dbaas.exception;

/**
 * Exception thrown when encryption or decryption operations fail.
 */
public class EncryptionException extends RuntimeException {

    private final String operation;

    public EncryptionException(String operation, String message) {
        super(String.format("%s failed: %s", operation, message));
        this.operation = operation;
    }

    public EncryptionException(String operation, String message, Throwable cause) {
        super(String.format("%s failed: %s", operation, message), cause);
        this.operation = operation;
    }

    public EncryptionException(String operation, Throwable cause) {
        super(String.format("%s failed: %s", operation, cause.getMessage()), cause);
        this.operation = operation;
    }

    public String getOperation() {
        return operation;
    }
}
