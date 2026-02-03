package com.dbaas.model.dto;

import com.dbaas.model.ClusterStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for Cluster entity.
 * Used to avoid exposing internal entity structure and sensitive data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterResponse {

    private String id;
    private String name;
    private String description;
    private String mysqlVersion;
    private int replicaCount;
    private ClusterStatus status;

    // Connection info (safe to expose)
    private String dbUser;
    private Integer proxyPort;

    // Feature flags
    private boolean enableOrchestrator;
    private boolean enableBackup;

    // Node count information
    private int totalNodes;
    private int runningNodes;

    // Error message if any
    private String errorMessage;

    // Timestamps
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Convert from entity to response DTO.
     * Excludes sensitive fields like passwords and internal container IDs.
     */
    public static ClusterResponse fromEntity(com.dbaas.model.Cluster cluster) {
        return ClusterResponse.builder()
                .id(cluster.getId())
                .name(cluster.getName())
                .description(cluster.getDescription())
                .mysqlVersion(cluster.getMysqlVersion())
                .replicaCount(cluster.getReplicaCount())
                .status(cluster.getStatus())
                .dbUser(cluster.getDbUser())
                .proxyPort(cluster.getProxyPort())
                .enableOrchestrator(cluster.isEnableOrchestrator())
                .enableBackup(cluster.isEnableBackup())
                .totalNodes(2 + cluster.getReplicaCount()) // master + proxy + replicas
                .errorMessage(cluster.getErrorMessage())
                .createdAt(cluster.getCreatedAt())
                .updatedAt(cluster.getUpdatedAt())
                .build();
    }

    /**
     * Convert from entity with node stats.
     */
    public static ClusterResponse fromEntity(com.dbaas.model.Cluster cluster, int runningNodes) {
        ClusterResponse response = fromEntity(cluster);
        response.setRunningNodes(runningNodes);
        return response;
    }
}
