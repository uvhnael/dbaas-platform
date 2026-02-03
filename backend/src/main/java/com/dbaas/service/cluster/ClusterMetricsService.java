package com.dbaas.service.cluster;

import com.dbaas.exception.ClusterNotFoundException;
import com.dbaas.model.Cluster;
import com.dbaas.model.Node;
import com.dbaas.model.dto.ClusterMetricsResponse;
import com.dbaas.model.dto.NodeStatsResponse;
import com.dbaas.repository.ClusterRepository;
import com.dbaas.repository.NodeRepository;
import com.dbaas.service.DockerService;
import com.dbaas.service.MonitoringService;
import com.dbaas.service.NodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for cluster metrics aggregation.
 * 
 * <p>
 * Extracted from ClusterController to follow layered architecture
 * - Controller should not access Repository directly.
 * </p>
 * 
 * <p>
 * Uses NodeService for reusable node stats collection methods.
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClusterMetricsService {

    private final ClusterRepository clusterRepository;
    private final NodeRepository nodeRepository;
    private final DockerService dockerService;
    private final MonitoringService monitoringService;
    private final NodeService nodeService;

    /**
     * Get aggregated metrics for all nodes in a cluster.
     * This method was previously in ClusterController.getClusterMetrics().
     */
    public ClusterMetricsResponse getClusterMetrics(String clusterId, String userId) {
        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new ClusterNotFoundException(clusterId));

        // Verify ownership
        if (!cluster.getUserId().equals(userId)) {
            throw new AccessDeniedException("Not authorized to access this cluster");
        }

        List<Node> nodes = nodeRepository.findByClusterId(clusterId);

        // Collect stats from each node using NodeService helper methods
        List<NodeStatsResponse> nodeStats = nodes.stream()
                .map(nodeService::getNodeStatsSafe)
                .toList();

        // Aggregation counters
        double totalCpu = 0;
        long totalMemUsage = 0, totalMemLimit = 0;
        long totalNetRx = 0, totalNetTx = 0;
        long totalBlockRead = 0, totalBlockWrite = 0;
        int runningCount = 0;

        // Aggregate from collected stats
        for (NodeStatsResponse stats : nodeStats) {
            if (stats.isRunning()) {
                runningCount++;
                totalCpu += stats.getCpuUsagePercent();
                totalMemUsage += stats.getMemoryUsage();
                totalMemLimit += stats.getMemoryLimit();
                totalNetRx += stats.getNetworkRxBytes();
                totalNetTx += stats.getNetworkTxBytes();
                totalBlockRead += stats.getBlockRead();
                totalBlockWrite += stats.getBlockWrite();
            }
        }

        // Get MySQL-specific metrics
        Map<String, Object> mysqlMetrics = monitoringService.getClusterMetrics(clusterId);

        return buildMetricsResponse(cluster, nodes, nodeStats, runningCount,
                totalCpu, totalMemUsage, totalMemLimit,
                totalNetRx, totalNetTx, totalBlockRead, totalBlockWrite,
                mysqlMetrics);
    }

    private ClusterMetricsResponse buildMetricsResponse(
            Cluster cluster,
            List<Node> nodes,
            List<NodeStatsResponse> nodeStats,
            int runningCount,
            double totalCpu,
            long totalMemUsage,
            long totalMemLimit,
            long totalNetRx,
            long totalNetTx,
            long totalBlockRead,
            long totalBlockWrite,
            Map<String, Object> mysqlMetrics) {

        double avgCpu = runningCount > 0 ? Math.round((totalCpu / runningCount) * 100.0) / 100.0 : 0;
        double avgMemPercent = totalMemLimit > 0
                ? Math.round((double) totalMemUsage / totalMemLimit * 10000.0) / 100.0
                : 0;

        return ClusterMetricsResponse.builder()
                .clusterId(cluster.getId())
                .clusterName(cluster.getName())
                .status(cluster.getStatus().name())
                .timestamp(Instant.now())
                .totalNodes(nodes.size())
                .runningNodes(runningCount)
                .avgCpuPercent(avgCpu)
                .totalMemoryUsage(totalMemUsage)
                .totalMemoryLimit(totalMemLimit)
                .avgMemoryPercent(avgMemPercent)
                .totalNetworkRx(totalNetRx)
                .totalNetworkTx(totalNetTx)
                .totalBlockRead(totalBlockRead)
                .totalBlockWrite(totalBlockWrite)
                .nodeMetrics(nodeStats)
                .queriesPerSecond((Double) mysqlMetrics.getOrDefault("queriesPerSecond", 0.0))
                .activeConnections((Integer) mysqlMetrics.getOrDefault("connections", 0))
                .replicationLagSeconds((Integer) mysqlMetrics.getOrDefault("replicationLag", 0))
                .build();
    }

    /**
     * Get metrics for a specific node.
     * Delegates to NodeService for reusability.
     */
    public NodeStatsResponse getNodeMetrics(String nodeId) {
        return nodeService.getNodeStats(nodeId);
    }

    /**
     * Get summary metrics for all clusters of a user.
     */
    public ClusterMetricsSummary getUserClustersSummary(String userId) {
        List<Cluster> clusters = clusterRepository.findByUserId(userId);

        int totalClusters = clusters.size();
        int runningClusters = 0;
        int totalNodes = 0;
        int runningNodes = 0;

        for (Cluster cluster : clusters) {
            List<Node> nodes = nodeRepository.findByClusterId(cluster.getId());
            totalNodes += nodes.size();

            switch (cluster.getStatus()) {
                case RUNNING, HEALTHY -> {
                    runningClusters++;
                    runningNodes += nodes.size();
                }
                case DEGRADED -> runningNodes += (int) nodes.stream()
                        .filter(n -> dockerService.isContainerHealthy(n.getContainerId()))
                        .count();
                default -> {
                    /* ignore */ }
            }
        }

        return new ClusterMetricsSummary(totalClusters, runningClusters, totalNodes, runningNodes);
    }

    /**
     * Summary of user's cluster metrics.
     */
    public record ClusterMetricsSummary(
            int totalClusters,
            int runningClusters,
            int totalNodes,
            int runningNodes) {
    }
}
