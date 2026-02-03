package com.dbaas.exception;

/**
 * Exception thrown when a container is not ready or healthy.
 */
public class ContainerNotReadyException extends RuntimeException {

    private final String containerId;
    private final String containerName;
    private final int waitedSeconds;

    public ContainerNotReadyException(String containerId, int waitedSeconds) {
        super(String.format("Container '%s' not ready after %d seconds", containerId, waitedSeconds));
        this.containerId = containerId;
        this.containerName = null;
        this.waitedSeconds = waitedSeconds;
    }

    public ContainerNotReadyException(String containerId, String containerName, int waitedSeconds) {
        super(String.format("Container '%s' (%s) not ready after %d seconds",
                containerName, containerId, waitedSeconds));
        this.containerId = containerId;
        this.containerName = containerName;
        this.waitedSeconds = waitedSeconds;
    }

    public ContainerNotReadyException(String containerId, String message, Throwable cause) {
        super(String.format("Container '%s' not ready: %s", containerId, message), cause);
        this.containerId = containerId;
        this.containerName = null;
        this.waitedSeconds = 0;
    }

    public String getContainerId() {
        return containerId;
    }

    public String getContainerName() {
        return containerName;
    }

    public int getWaitedSeconds() {
        return waitedSeconds;
    }
}
