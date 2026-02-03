package com.dbaas.exception;

/**
 * Exception thrown when cluster provisioning fails.
 */
public class ClusterProvisioningException extends RuntimeException {

    private final String clusterId;
    private final String phase;

    public ClusterProvisioningException(String clusterId, String message) {
        super(message);
        this.clusterId = clusterId;
        this.phase = null;
    }

    public ClusterProvisioningException(String clusterId, String phase, String message) {
        super(String.format("Cluster '%s' provisioning failed at phase '%s': %s", clusterId, phase, message));
        this.clusterId = clusterId;
        this.phase = phase;
    }

    public ClusterProvisioningException(String clusterId, String phase, Throwable cause) {
        super(String.format("Cluster '%s' provisioning failed at phase '%s': %s", clusterId, phase, cause.getMessage()),
                cause);
        this.clusterId = clusterId;
        this.phase = phase;
    }

    public String getClusterId() {
        return clusterId;
    }

    public String getPhase() {
        return phase;
    }
}
