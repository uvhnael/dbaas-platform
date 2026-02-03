package com.dbaas.model.dto;

import com.dbaas.model.ClusterStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Detailed response DTO for Cluster entity with node information.
 * Used when full cluster details are needed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterDetailResponse {

    private String id;
    private String name;
    private String description;
    private String mysqlVersion;
    private int replicaCount;
    private ClusterStatus status;

    // Connection info
    private String dbUser;
    private Integer proxyPort;
    private String connectionHost;

    // Feature flags
    private boolean enableOrchestrator;
    private boolean enableBackup;

    // Nodes
    private List<NodeResponse> nodes;
    private NodeSummary nodeSummary;

    // Error message if any
    private String errorMessage;

    // Timestamps
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Summary of node statuses.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeSummary {
        private int totalNodes;
        private int runningNodes;
        private int stoppedNodes;
        private int failedNodes;

        private boolean masterHealthy;
        private boolean proxyHealthy;
        private int healthyReplicas;
    }

    /**
     * Convert from entity with nodes.
     */
    public static ClusterDetailResponse fromEntity(
            com.dbaas.model.Cluster cluster,
            List<com.dbaas.model.Node> nodes) {

        List<NodeResponse> nodeResponses = nodes.stream()
                .map(NodeResponse::fromEntity)
                .toList();

        int running = (int) nodes.stream()
                .filter(n -> n.getStatus() == com.dbaas.model.NodeStatus.RUNNING)
                .count();
        int stopped = (int) nodes.stream()
                .filter(n -> n.getStatus() == com.dbaas.model.NodeStatus.STOPPED)
                .count();
        int failed = (int) nodes.stream()
                .filter(n -> n.getStatus() == com.dbaas.model.NodeStatus.FAILED)
                .count();

        NodeSummary summary = NodeSummary.builder()
                .totalNodes(nodes.size())
                .runningNodes(running)
                .stoppedNodes(stopped)
                .failedNodes(failed)
                .build();

        return ClusterDetailResponse.builder()
                .id(cluster.getId())
                .name(cluster.getName())
                .description(cluster.getDescription())
                .mysqlVersion(cluster.getMysqlVersion())
                .replicaCount(cluster.getReplicaCount())
                .status(cluster.getStatus())
                .dbUser(cluster.getDbUser())
                .proxyPort(cluster.getProxyPort())
                .connectionHost("proxysql-" + cluster.getId())
                .enableOrchestrator(cluster.isEnableOrchestrator())
                .enableBackup(cluster.isEnableBackup())
                .nodes(nodeResponses)
                .nodeSummary(summary)
                .errorMessage(cluster.getErrorMessage())
                .createdAt(cluster.getCreatedAt())
                .updatedAt(cluster.getUpdatedAt())
                .build();
    }
}
