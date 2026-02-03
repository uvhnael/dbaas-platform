package com.dbaas.service;

import com.dbaas.exception.NodeNotFoundException;
import com.dbaas.model.Node;
import com.dbaas.model.NodeRole;
import com.dbaas.model.dto.ClusterLogsResponse;
import com.dbaas.model.dto.NodeLogsResponse;
import com.dbaas.model.dto.NodeStatsResponse;
import com.dbaas.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Service for node management operations.
 * 
 * <p>
 * Extracted from NodeController to follow Single Responsibility Principle.
 * Controller should only handle HTTP concerns, not business logic.
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NodeService {

    private final NodeRepository nodeRepository;
    private final DockerService dockerService;
    private final MonitoringService monitoringService;

    /**
     * List all nodes in a cluster.
     * 
     * @param clusterId The cluster ID
     * @return List of nodes
     */
    public List<Node> listNodes(String clusterId) {
        return nodeRepository.findByClusterId(clusterId);
    }

    /**
     * Get node by ID.
     * 
     * @param nodeId The node ID
     * @return Node entity
     * @throws NodeNotFoundException if node not found
     */
    public Node getNode(String nodeId) {
        return nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
    }

    /**
     * Get node container statistics (CPU, memory, network, disk I/O).
     * 
     * @param nodeId The node ID
     * @return NodeStatsResponse with container stats
     */
    public NodeStatsResponse getNodeStats(String nodeId) {
        Node node = getNode(nodeId);
        return getNodeStatsInternal(node);
    }

    /**
     * Get stats for all nodes in a cluster.
     * 
     * @param clusterId The cluster ID
     * @return List of NodeStatsResponse for all nodes
     */
    public List<NodeStatsResponse> getAllNodesStats(String clusterId) {
        List<Node> nodes = nodeRepository.findByClusterId(clusterId);
        return nodes.stream()
                .map(this::getNodeStatsSafe)
                .toList();
    }

    // ========================================================================
    // REUSABLE HELPER METHODS - Can be used by other services
    // ========================================================================

    /**
     * Get stats for a node with enriched role and replication lag.
     * Throws exception if stats collection fails.
     * 
     * <p>
     * Reusable by: ClusterMetricsService, DashboardService
     * </p>
     * 
     * @param node The node entity
     * @return NodeStatsResponse with enriched data
     */
    public NodeStatsResponse getNodeStatsInternal(Node node) {
        NodeStatsResponse stats = dockerService.getContainerStats(node.getContainerName(), node.getId());
        enrichNodeStats(node, stats);
        return stats;
    }

    /**
     * Get stats for a node safely - returns error stats on failure.
     * 
     * <p>
     * Reusable by: ClusterMetricsService, DashboardService
     * </p>
     * 
     * @param node The node entity
     * @return NodeStatsResponse (either real stats or error placeholder)
     */
    public NodeStatsResponse getNodeStatsSafe(Node node) {
        try {
            return getNodeStatsInternal(node);
        } catch (Exception e) {
            log.warn("Failed to get stats for node '{}': {}", node.getContainerName(), e.getMessage());
            return createErrorStats(node);
        }
    }

    /**
     * Enrich NodeStatsResponse with node role and replication lag.
     * 
     * <p>
     * Reusable by: ClusterMetricsService
     * </p>
     * 
     * @param node  The node entity
     * @param stats The stats response to enrich
     */
    public void enrichNodeStats(Node node, NodeStatsResponse stats) {
        stats.setNodeRole(node.getRole() != null ? node.getRole().name() : null);
        if (node.getRole() == NodeRole.REPLICA) {
            stats.setReplicationLagSeconds(
                    monitoringService.getNodeReplicationLag(node.getContainerId(), node.getClusterId()));
        }
    }

    /**
     * Create error stats placeholder for a node that failed to respond.
     * 
     * <p>
     * Reusable by: ClusterMetricsService, DashboardService
     * </p>
     * 
     * @param node The node entity
     * @return NodeStatsResponse with error state
     */
    public NodeStatsResponse createErrorStats(Node node) {
        return NodeStatsResponse.builder()
                .nodeId(node.getId())
                .containerName(node.getContainerName())
                .nodeRole(node.getRole() != null ? node.getRole().name() : null)
                .timestamp(Instant.now())
                .running(false)
                .state("error")
                .build();
    }

    /**
     * Get node container logs.
     * 
     * @param nodeId     The node ID
     * @param lines      Number of log lines to retrieve
     * @param timestamps Include timestamps in log output
     * @return NodeLogsResponse with container logs
     */
    public NodeLogsResponse getNodeLogs(String nodeId, int lines, boolean timestamps) {
        Node node = getNode(nodeId);
        String logs = dockerService.getContainerLogs(node.getContainerName(), lines, timestamps);
        return new NodeLogsResponse(nodeId, node.getContainerName(), logs);
    }

    /**
     * Get all cluster container logs.
     * 
     * @param clusterId  The cluster ID
     * @param lines      Number of log lines per node
     * @param timestamps Include timestamps in log output
     * @return ClusterLogsResponse with logs from all nodes
     */
    public ClusterLogsResponse getClusterLogs(String clusterId, int lines, boolean timestamps) {
        List<Node> nodes = nodeRepository.findByClusterId(clusterId);
        List<NodeLogsResponse> nodeLogs = nodes.stream()
                .map(node -> new NodeLogsResponse(
                        node.getId(),
                        node.getContainerName(),
                        dockerService.getContainerLogs(node.getContainerName(), lines, timestamps)))
                .toList();

        return new ClusterLogsResponse(clusterId, nodeLogs);
    }
}
