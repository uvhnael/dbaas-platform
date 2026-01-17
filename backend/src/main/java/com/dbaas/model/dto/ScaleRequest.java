package com.dbaas.model.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for scaling a cluster.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScaleRequest {

    @NotNull(message = "Replica count is required")
    @Min(value = 0, message = "Replica count cannot be negative")
    @Max(value = 5, message = "Maximum 5 replicas allowed")
    private Integer replicaCount;

    /**
     * Optional: Update resources during scaling.
     */
    private ResourceUpdateConfig resources;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceUpdateConfig {
        @Min(value = 1, message = "CPU must be at least 1 core")
        @Max(value = 16, message = "CPU cannot exceed 16 cores")
        private Integer cpuCores;

        @Pattern(regexp = "^[1-9][0-9]*[GMK]$", message = "Memory must be in format like 4G, 512M")
        private String memory;
    }
}
