package com.dbaas.exception;

import com.dbaas.model.ClusterStatus;

/**
 * Exception thrown when cluster is in an invalid state for the requested
 * operation.
 */
public class InvalidClusterStateException extends RuntimeException {

    private final String clusterId;
    private final ClusterStatus currentStatus;
    private final ClusterStatus expectedStatus;

    public InvalidClusterStateException(String clusterId, ClusterStatus currentStatus, String message) {
        super(String.format("Invalid state for cluster '%s' (current: %s): %s",
                clusterId, currentStatus, message));
        this.clusterId = clusterId;
        this.currentStatus = currentStatus;
        this.expectedStatus = null;
    }

    public InvalidClusterStateException(String clusterId, ClusterStatus currentStatus, ClusterStatus expectedStatus) {
        super(String.format("Cluster '%s' is in state '%s', expected '%s'",
                clusterId, currentStatus, expectedStatus));
        this.clusterId = clusterId;
        this.currentStatus = currentStatus;
        this.expectedStatus = expectedStatus;
    }

    public String getClusterId() {
        return clusterId;
    }

    public ClusterStatus getCurrentStatus() {
        return currentStatus;
    }

    public ClusterStatus getExpectedStatus() {
        return expectedStatus;
    }
}
