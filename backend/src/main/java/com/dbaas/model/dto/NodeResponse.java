package com.dbaas.model.dto;

import com.dbaas.model.NodeRole;
import com.dbaas.model.NodeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for Node entity.
 * Used to avoid exposing internal entity structure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeResponse {

    private String id;
    private String clusterId;
    private String containerName;
    private NodeRole role;
    private NodeStatus status;
    private String ipAddress;
    private int port;

    // Resource configuration
    private int cpuCores;
    private String memory;
    private String storage;

    private boolean readOnly;
    private Instant createdAt;

    /**
     * Convert from entity to response DTO.
     * Excludes internal container ID.
     */
    public static NodeResponse fromEntity(com.dbaas.model.Node node) {
        return NodeResponse.builder()
                .id(node.getId())
                .clusterId(node.getClusterId())
                .containerName(node.getContainerName())
                .role(node.getRole())
                .status(node.getStatus())
                .ipAddress(node.getIpAddress())
                .port(node.getPort())
                .cpuCores(node.getCpuCores())
                .memory(node.getMemory())
                .storage(node.getStorage())
                .readOnly(node.isReadOnly())
                .createdAt(node.getCreatedAt())
                .build();
    }
}
