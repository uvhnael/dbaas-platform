package com.dbaas.controller;

import com.dbaas.model.Cluster;
import com.dbaas.model.ClusterStatus;
import com.dbaas.model.Node;
import com.dbaas.model.User;
import com.dbaas.model.dto.ApiResponse;
import com.dbaas.model.dto.DashboardSummaryResponse;
import com.dbaas.model.dto.NodeStatsResponse;
import com.dbaas.repository.ClusterRepository;
import com.dbaas.repository.NodeRepository;
import com.dbaas.service.DockerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * REST Controller for dashboard summary.
 * Provides overall system overview and aggregated statistics.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Dashboard Summary API")
@Slf4j
public class DashboardController {

    private final ClusterRepository clusterRepository;
    private final NodeRepository nodeRepository;
    private final DockerService dockerService;

    /**
     * Get overall dashboard summary with cluster counts, node health, and resource
     * usage.
     */
    @GetMapping("/summary")
    @Operation(summary = "Get dashboard summary with cluster and node statistics")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getDashboardSummary(
            Authentication authentication) {

        String userId = getUserId(authentication);
        List<Cluster> clusters = clusterRepository.findByUserId(userId);

        // Count clusters by status
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

        // Get all nodes and their health
        List<Node> allNodes = new ArrayList<>();
        for (Cluster cluster : clusters) {
            allNodes.addAll(nodeRepository.findByClusterId(cluster.getId()));
        }

        int healthyNodes = 0, unhealthyNodes = 0;
        double totalCpu = 0;
        long totalMemUsage = 0, totalMemLimit = 0;
        long totalNetRx = 0, totalNetTx = 0;
        int runningNodeCount = 0;

        for (Node node : allNodes) {
            try {
                NodeStatsResponse stats = dockerService.getContainerStats(
                        node.getContainerName(), node.getId());
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
            } catch (Exception e) {
                unhealthyNodes++;
                log.warn("Failed to get stats for node: {}", node.getId());
            }
        }

        // Build cluster summaries
        List<DashboardSummaryResponse.ClusterSummary> clusterSummaries = clusters.stream()
                .map(this::buildClusterSummary)
                .toList();

        DashboardSummaryResponse response = DashboardSummaryResponse.builder()
                .timestamp(Instant.now())
                .totalClusters(clusters.size())
                .runningClusters(running)
                .stoppedClusters(stopped)
                .failedClusters(failed)
                .totalNodes(allNodes.size())
                .healthyNodes(healthyNodes)
                .unhealthyNodes(unhealthyNodes)
                .resourceUsage(DashboardSummaryResponse.ResourceUsage.builder()
                        .avgCpuPercent(runningNodeCount > 0 ? totalCpu / runningNodeCount : 0)
                        .totalMemoryUsage(totalMemUsage)
                        .totalMemoryLimit(totalMemLimit)
                        .avgMemoryPercent(totalMemLimit > 0 ? (double) totalMemUsage / totalMemLimit * 100 : 0)
                        .totalNetworkRx(totalNetRx)
                        .totalNetworkTx(totalNetTx)
                        .build())
                .clusters(clusterSummaries)
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // === Helper methods ===

    private DashboardSummaryResponse.ClusterSummary buildClusterSummary(Cluster cluster) {
        List<Node> nodes = nodeRepository.findByClusterId(cluster.getId());
        int healthyCount = 0;

        for (Node node : nodes) {
            try {
                NodeStatsResponse stats = dockerService.getContainerStats(
                        node.getContainerName(), node.getId());
                if (stats.isRunning()) {
                    healthyCount++;
                }
            } catch (Exception e) {
                // Node is not healthy
            }
        }

        return DashboardSummaryResponse.ClusterSummary.builder()
                .clusterId(cluster.getId())
                .name(cluster.getName())
                .status(cluster.getStatus().name())
                .nodeCount(nodes.size())
                .healthyNodeCount(healthyCount)
                .build();
    }

    private String getUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return "anonymous";
    }
}
