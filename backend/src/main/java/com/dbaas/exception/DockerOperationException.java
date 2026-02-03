package com.dbaas.exception;

/**
 * Exception thrown when a Docker operation fails.
 */
public class DockerOperationException extends RuntimeException {

    private final String operation;
    private final String containerId;

    public DockerOperationException(String message) {
        super(message);
        this.operation = null;
        this.containerId = null;
    }

    public DockerOperationException(String operation, String message) {
        super(String.format("Docker operation '%s' failed: %s", operation, message));
        this.operation = operation;
        this.containerId = null;
    }

    public DockerOperationException(String operation, String containerId, String message) {
        super(String.format("Docker operation '%s' on container '%s' failed: %s", operation, containerId, message));
        this.operation = operation;
        this.containerId = containerId;
    }

    public DockerOperationException(String operation, Throwable cause) {
        super(String.format("Docker operation '%s' failed: %s", operation, cause.getMessage()), cause);
        this.operation = operation;
        this.containerId = null;
    }

    public String getOperation() {
        return operation;
    }

    public String getContainerId() {
        return containerId;
    }
}
