package com.dbaas.exception;

/**
 * Exception thrown when Orchestrator operations fail.
 */
public class OrchestratorException extends RuntimeException {

    private final String operation;
    private final String clusterId;

    public OrchestratorException(String message) {
        super(message);
        this.operation = null;
        this.clusterId = null;
    }

    public OrchestratorException(String operation, String message) {
        super(String.format("Orchestrator operation '%s' failed: %s", operation, message));
        this.operation = operation;
        this.clusterId = null;
    }

    public OrchestratorException(String operation, String clusterId, String message) {
        super(String.format("Orchestrator operation '%s' on cluster '%s' failed: %s",
                operation, clusterId, message));
        this.operation = operation;
        this.clusterId = clusterId;
    }

    public OrchestratorException(String operation, Throwable cause) {
        super(String.format("Orchestrator operation '%s' failed: %s", operation, cause.getMessage()), cause);
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
