package com.dbaas.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Request DTO for creating a new cluster.
 * Contains all configuration options for cluster provisioning.
 * Supports per-node resource configuration for master and each replica.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateClusterRequest {

    @NotBlank(message = "Cluster name is required")
    @Size(min = 3, max = 50, message = "Cluster name must be 3-50 characters")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9-_]*$", message = "Cluster name must start with letter and contain only alphanumeric, dash, underscore")
    private String name;

    @Pattern(regexp = "^[58]\\.[0-9]$", message = "MySQL version must be 5.x or 8.x format (e.g., 8.0)")
    @Builder.Default
    private String mysqlVersion = "8.0";

    @Min(value = 0, message = "Replica count cannot be negative")
    @Max(value = 5, message = "Maximum 5 replicas allowed")
    @Builder.Default
    private int replicaCount = 2;

    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;

    /**
     * Default resource configuration for all nodes (used when nodeConfigs is not provided).
     * @deprecated Use nodeConfigs for per-node configuration
     */
    @Builder.Default
    private ResourceConfig resources = new ResourceConfig();

    /**
     * Master node configuration.
     * If not provided, uses default resources.
     */
    @Valid
    private NodeConfig masterConfig;

    /**
     * Per-replica configurations.
     * Index 0 = Replica 1, Index 1 = Replica 2, etc.
     * If a replica config is not provided, uses default resources.
     */
    @Valid
    @Builder.Default
    private List<NodeConfig> replicaConfigs = new ArrayList<>();

    /**
     * Feature flags for cluster components.
     */
    @Builder.Default
    private FeatureConfig features = new FeatureConfig();

    /**
     * Per-node resource configuration DTO.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeConfig {
        @Min(value = 1, message = "CPU must be at least 1 core")
        @Max(value = 16, message = "CPU cannot exceed 16 cores")
        @Builder.Default
        private int cpuCores = 2;

        @Pattern(regexp = "^[1-9][0-9]*[GMK]$", message = "Memory must be in format like 4G, 512M, 1024M")
        @Builder.Default
        private String memory = "4G";

        @Pattern(regexp = "^[1-9][0-9]*[GMK]$", message = "Storage must be in format like 10G, 100G")
        @Builder.Default
        private String storage = "10G";
    }

    /**
     * Default resource configuration DTO (for backward compatibility).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceConfig {
        @Min(value = 1, message = "CPU must be at least 1 core")
        @Max(value = 16, message = "CPU cannot exceed 16 cores")
        @Builder.Default
        private int cpuCores = 2;

        @Pattern(regexp = "^[1-9][0-9]*[GMK]$", message = "Memory must be in format like 4G, 512M, 1024M")
        @Builder.Default
        private String memory = "4G";

        @Pattern(regexp = "^[1-9][0-9]*[GMK]$", message = "Storage must be in format like 10G, 100G")
        @Builder.Default
        private String storage = "10G";
    }

    /**
     * Feature flags DTO.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeatureConfig {
        @Builder.Default
        private boolean enableOrchestrator = true;

        @Builder.Default
        private boolean enableBackup = false;
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Get configuration for master node.
     * Falls back to default resources if masterConfig is not set.
     */
    public NodeConfig getMasterNodeConfig() {
        if (masterConfig != null) {
            return masterConfig;
        }
        // Fallback to default resources
        return NodeConfig.builder()
                .cpuCores(resources != null ? resources.getCpuCores() : 2)
                .memory(resources != null ? resources.getMemory() : "4G")
                .storage(resources != null ? resources.getStorage() : "10G")
                .build();
    }

    /**
     * Get configuration for a specific replica (1-indexed).
     * Falls back to default resources if not configured.
     * 
     * @param replicaNumber 1-indexed replica number
     * @return NodeConfig for the replica
     */
    public NodeConfig getReplicaNodeConfig(int replicaNumber) {
        int index = replicaNumber - 1;
        if (replicaConfigs != null && index >= 0 && index < replicaConfigs.size()) {
            NodeConfig config = replicaConfigs.get(index);
            if (config != null) {
                return config;
            }
        }
        // Fallback to default resources
        return NodeConfig.builder()
                .cpuCores(resources != null ? resources.getCpuCores() : 2)
                .memory(resources != null ? resources.getMemory() : "4G")
                .storage(resources != null ? resources.getStorage() : "10G")
                .build();
    }
}
