package com.dbaas.exception;

/**
 * Exception thrown when ProxySQL operations fail.
 */
public class ProxySQLException extends RuntimeException {

    private final String operation;
    private final String clusterId;

    public ProxySQLException(String message) {
        super(message);
        this.operation = null;
        this.clusterId = null;
    }

    public ProxySQLException(String operation, String message) {
        super(String.format("ProxySQL operation '%s' failed: %s", operation, message));
        this.operation = operation;
        this.clusterId = null;
    }

    public ProxySQLException(String operation, String clusterId, String message) {
        super(String.format("ProxySQL operation '%s' on cluster '%s' failed: %s",
                operation, clusterId, message));
        this.operation = operation;
        this.clusterId = clusterId;
    }

    public ProxySQLException(String operation, Throwable cause) {
        super(String.format("ProxySQL operation '%s' failed: %s", operation, cause.getMessage()), cause);
        this.operation = operation;
        this.clusterId = null;
    }

    public String getOperation() {
        return operation;
    }

    public String getClusterId() {
        return clusterId;
    }
}
