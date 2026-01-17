package com.dbaas.model;

/**
 * Status of a cluster.
 */
public enum ClusterStatus {
    PROVISIONING,
    HEALTHY,
    RUNNING, // Kept for backwards compatibility, same as HEALTHY
    DEGRADED,
    STOPPED,
    DELETING,
    FAILED
}
