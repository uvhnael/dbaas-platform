package com.dbaas.service;

import com.dbaas.model.Cluster;
import com.dbaas.model.Node;
import com.dbaas.model.dto.DashboardSummaryResponse;
import com.dbaas.model.dto.NodeStatsResponse;
import com.dbaas.repository.ClusterRepository;
import com.dbaas.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for dashboard summary and aggregated statistics.
 * 
 * <p>
 * Extracted from DashboardController to follow Single Responsibility Principle.
 * Controller should only handle HTTP concerns, not business logic.
 * </p>
 * 
 * <p>
 * Uses NodeService for reusable node stats collection methods.
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DashboardService {

    private final ClusterRepository clusterRepository;
    private final NodeRepository nodeRepository;
    private final NodeService nodeService;

    /**
     * Get overall dashboard summary with cluster counts, node health, and resource
     * usage.
     * 
     * @param userId The user ID to filter clusters
     * @return DashboardSummaryResponse with aggregated statistics
     */
    public DashboardSummaryResponse getDashboardSummary(String userId) {
        List<Cluster> clusters = clusterRepository.findByUserId(userId);

        // Count clusters by status
        ClusterCounts clusterCounts = countClustersByStatus(clusters);

        // Get all nodes and their health
        List<Node> allNodes = getAllNodesForClusters(clusters);

        // Aggregate resource usage
        ResourceAggregation resources = aggregateResourceUsage(allNodes);

        // Build cluster summaries
        List<DashboardSummaryResponse.ClusterSummary> clusterSummaries = clusters.stream()
                .map(this::buildClusterSummary)
                .toList();

        return DashboardSummaryResponse.builder()
                .timestamp(Instant.now())
                .totalClusters(clusters.size())
                .runningClusters(clusterCounts.running)
                .stoppedClusters(clusterCounts.stopped)
                .failedClusters(clusterCounts.failed)
                .totalNodes(allNodes.size())
                .healthyNodes(resources.healthyNodes)
                .unhealthyNodes(resources.unhealthyNodes)
                .resourceUsage(DashboardSummaryResponse.ResourceUsage.builder()
                        .avgCpuPercent(resources.runningNodeCount > 0
                                ? resources.totalCpu / resources.runningNodeCount
                                : 0)
                        .totalMemoryUsage(resources.totalMemUsage)
                        .totalMemoryLimit(resources.totalMemLimit)
                        .avgMemoryPercent(resources.totalMemLimit > 0
                                ? (double) resources.totalMemUsage / resources.totalMemLimit * 100
                                : 0)
                        .totalNetworkRx(resources.totalNetRx)
                        .totalNetworkTx(resources.totalNetTx)
                        .build())
                .clusters(clusterSummaries)
                .build();
    }

    /**
     * Count clusters by status.
     */
    private ClusterCounts countClustersByStatus(List<Cluster> clusters) {
        int running = 0, stopped = 0, failed = 0;
        for (Cluster cluster : clusters) {
            switch (cluster.getStatus()) {
                case RUNNING, HEALTHY -> running++;
                case STOPPED -> stopped++;
                case FAILED, DEGRADED -> failed++;
                default -> {
                }
            }
        }
        return new ClusterCounts(running, stopped, failed);
    }

    /**
     * Get all nodes for a list of clusters.
     */
    private List<Node> getAllNodesForClusters(List<Cluster> clusters) {
        List<Node> allNodes = new ArrayList<>();
        for (Cluster cluster : clusters) {
            allNodes.addAll(nodeRepository.findByClusterId(cluster.getId()));
        }
        return allNodes;
    }

    /**
     * Aggregate resource usage from all nodes.
     * Uses NodeService for reusable stats collection.
     */
    private ResourceAggregation aggregateResourceUsage(List<Node> allNodes) {
        int healthyNodes = 0, unhealthyNodes = 0;
        double totalCpu = 0;
        long totalMemUsage = 0, totalMemLimit = 0;
        long totalNetRx = 0, totalNetTx = 0;
        int runningNodeCount = 0;

        for (Node node : allNodes) {
            NodeStatsResponse stats = nodeService.getNodeStatsSafe(node);
            if (stats.isRunning()) {
                healthyNodes++;
                runningNodeCount++;
                totalCpu += stats.getCpuUsagePercent();
                totalMemUsage += stats.getMemoryUsage();
                totalMemLimit += stats.getMemoryLimit();
                totalNetRx += stats.getNetworkRxBytes();
                totalNetTx += stats.getNetworkTxBytes();
            } else {
                unhealthyNodes++;
            }
        }

        return new ResourceAggregation(
                healthyNodes, unhealthyNodes, runningNodeCount,
                totalCpu, totalMemUsage, totalMemLimit, totalNetRx, totalNetTx);
    }

    /**
     * Build cluster summary for a single cluster.
     * Uses NodeService for reusable stats collection.
     */
    private DashboardSummaryResponse.ClusterSummary buildClusterSummary(Cluster cluster) {
        List<Node> nodes = nodeRepository.findByClusterId(cluster.getId());

        long healthyCount = nodes.stream()
                .map(nodeService::getNodeStatsSafe)
                .filter(NodeStatsResponse::isRunning)
                .count();

        return DashboardSummaryResponse.ClusterSummary.builder()
                .clusterId(cluster.getId())
                .name(cluster.getName())
                .status(cluster.getStatus().name())
                .nodeCount(nodes.size())
                .healthyNodeCount((int) healthyCount)
                .build();
    }

    // === Inner record classes for intermediate data ===

    private record ClusterCounts(int running, int stopped, int failed) {
    }

    private record ResourceAggregation(
            int healthyNodes, int unhealthyNodes, int runningNodeCount,
            double totalCpu, long totalMemUsage, long totalMemLimit,
            long totalNetRx, long totalNetTx) {
    }
}
