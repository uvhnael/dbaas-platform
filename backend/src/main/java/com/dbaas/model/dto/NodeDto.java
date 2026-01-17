package com.dbaas.model.dto;

import com.dbaas.model.NodeRole;
import com.dbaas.model.NodeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for node details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeDto {

    private String id;
    private String clusterId;
    private String containerName;
    private NodeRole role;
    private String ipAddress;
    private int port;
    private NodeStatus status;
    private String containerId;
    private boolean readOnly;

    /**
     * Health check info.
     */
    private HealthInfo health;

    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Health information DTO.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthInfo {
        private boolean healthy;
        private String replicationStatus;
        private long replicationLag;
        private long uptime;
    }
}
