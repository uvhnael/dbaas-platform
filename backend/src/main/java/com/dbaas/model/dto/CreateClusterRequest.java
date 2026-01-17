package com.dbaas.model.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new cluster.
 * Contains all configuration options for cluster provisioning.
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
     * Resource configuration for each node.
     */
    @Builder.Default
    private ResourceConfig resources = new ResourceConfig();

    /**
     * Feature flags for cluster components.
     */
    @Builder.Default
    private FeatureConfig features = new FeatureConfig();

    /**
     * Resource configuration DTO.
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
}
