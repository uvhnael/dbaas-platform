package com.dbaas.model.dto;

import com.dbaas.model.ClusterStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for cluster details.
 * Used to serialize cluster data without exposing entity internals.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterDto {

    private String id;
    private String name;
    private String ownerId;
    private String ownerUsername;
    private String mysqlVersion;
    private int replicaCount;
    private ClusterStatus status;
    private String networkId;

    /**
     * Connection info for clients.
     */
    private ConnectionInfo connection;

    /**
     * List of nodes in this cluster.
     */
    private List<NodeDto> nodes;

    /**
     * Resource allocation.
     */
    private ResourceInfo resources;

    private String errorMessage;
    private String description;

    // Feature flags
    private boolean enableOrchestrator;
    private boolean enableBackup;

    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Connection information DTO.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionInfo {
        private String writeHost;
        private int writePort;
        private String readHost;
        private int readPort;
        private String username;
        private String jdbcUrl;
    }

    /**
     * Resource information DTO.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceInfo {
        private int cpuCores;
        private String memory;
    }
}
