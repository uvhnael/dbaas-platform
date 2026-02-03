package com.dbaas.exception;

/**
 * Exception thrown when MySQL replication setup fails.
 */
public class ReplicationSetupException extends RuntimeException {

    private final String clusterId;
    private final String replicaId;

    public ReplicationSetupException(String clusterId, String message) {
        super(String.format("Replication setup failed for cluster '%s': %s", clusterId, message));
        this.clusterId = clusterId;
        this.replicaId = null;
    }

    public ReplicationSetupException(String clusterId, String replicaId, String message) {
        super(String.format("Replication setup failed for replica '%s' in cluster '%s': %s",
                replicaId, clusterId, message));
        this.clusterId = clusterId;
        this.replicaId = replicaId;
    }

    public ReplicationSetupException(String clusterId, String replicaId, Throwable cause) {
        super(String.format("Replication setup failed for replica '%s' in cluster '%s': %s",
                replicaId, clusterId, cause.getMessage()), cause);
        this.clusterId = clusterId;
        this.replicaId = replicaId;
    }

    public String getClusterId() {
        return clusterId;
    }

    public String getReplicaId() {
        return replicaId;
    }
}
