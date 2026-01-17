package com.dbaas.exception;

/**
 * Exception thrown when a cluster is not found.
 */
public class ClusterNotFoundException extends RuntimeException {

    public ClusterNotFoundException(String clusterId) {
        super("Cluster not found: " + clusterId);
    }
}
