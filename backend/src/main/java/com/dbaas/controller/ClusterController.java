package com.dbaas.controller;

import com.dbaas.model.Cluster;
import com.dbaas.model.ClusterStatus;
import com.dbaas.model.Node;
import com.dbaas.model.User;
import com.dbaas.model.dto.ApiResponse;
import com.dbaas.model.dto.ClusterMetricsResponse;
import com.dbaas.model.dto.CreateClusterRequest;
import com.dbaas.model.dto.NodeStatsResponse;
import com.dbaas.model.dto.ScaleRequest;
import com.dbaas.repository.NodeRepository;
import com.dbaas.service.ClusterService;
import com.dbaas.service.DockerService;
import com.dbaas.service.MonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * REST Controller for cluster management.
 */
@RestController
@RequestMapping("/api/v1/clusters")
@RequiredArgsConstructor
@Tag(name = "Clusters", description = "MySQL Cluster Management API")
public class ClusterController {

    private final ClusterService clusterService;
    private final MonitoringService monitoringService;
    private final DockerService dockerService;
    private final NodeRepository nodeRepository;

    @PostMapping
    @Operation(summary = "Create a new cluster")
    public ResponseEntity<ApiResponse<Cluster>> createCluster(
            @Valid @RequestBody CreateClusterRequest request,
            Authentication authentication) {

        String userId = getUserId(authentication);
        Cluster cluster = clusterService.createCluster(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(cluster, "Cluster created successfully"));
    }

    @GetMapping
    @Operation(summary = "List all clusters")
    public ResponseEntity<ApiResponse<List<Cluster>>> listClusters(Authentication authentication) {
        String userId = getUserId(authentication);
        List<Cluster> clusters = clusterService.listClusters(userId);
        return ResponseEntity.ok(ApiResponse.success(clusters));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get cluster details")
    public ResponseEntity<ApiResponse<Cluster>> getCluster(@PathVariable String id) {
        Cluster cluster = clusterService.getCluster(id);
        return ResponseEntity.ok(ApiResponse.success(cluster));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a cluster")
    public ResponseEntity<ApiResponse<Void>> deleteCluster(@PathVariable String id) {
        clusterService.deleteCluster(id);
        return ResponseEntity.ok(ApiResponse.success("Cluster deleted successfully"));
    }

    @PostMapping("/{id}/scale")
    @Operation(summary = "Scale cluster replicas")
    public ResponseEntity<ApiResponse<Cluster>> scaleCluster(
            @PathVariable String id,
            @Valid @RequestBody ScaleRequest request) {

        Cluster cluster = clusterService.scaleCluster(id, request);
        return ResponseEntity.ok(ApiResponse.success(cluster, "Cluster scaled successfully"));
    }

    @GetMapping("/{id}/health")
    @Operation(summary = "Get cluster health status")
    public ResponseEntity<ApiResponse<ClusterHealthResponse>> getClusterHealth(@PathVariable String id) {
        ClusterStatus status = clusterService.getClusterHealth(id);
        ClusterHealthResponse health = new ClusterHealthResponse(
                id, status, status == ClusterStatus.RUNNING || status == ClusterStatus.HEALTHY);
        return ResponseEntity.ok(ApiResponse.success(health));
    }

    @GetMapping("/{id}/metrics")
    @Operation(summary = "Get aggregated metrics for all nodes in a cluster")
    public ResponseEntity<ApiResponse<ClusterMetricsResponse>> getClusterMetrics(
            @PathVariable String id) {

        Cluster cluster = clusterService.getCluster(id);
        List<Node> nodes = nodeRepository.findByClusterId(id);
        List<NodeStatsResponse> nodeStats = new ArrayList<>();

        double totalCpu = 0;
        long totalMemUsage = 0, totalMemLimit = 0;
        long totalNetRx = 0, totalNetTx = 0;
        long totalBlockRead = 0, totalBlockWrite = 0;
        int runningCount = 0;

        for (Node node : nodes) {
            try {
                NodeStatsResponse stats = dockerService.getContainerStats(
                        node.getContainerName(), node.getId());
                nodeStats.add(stats);

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
            } catch (Exception e) {
                nodeStats.add(NodeStatsResponse.builder()
                        .nodeId(node.getId())
                        .containerName(node.getContainerName())
                        .timestamp(Instant.now())
                        .running(false)
                        .state("error")
                        .build());
            }
        }

        var mysqlMetrics = monitoringService.getClusterMetrics(id);

        ClusterMetricsResponse response = ClusterMetricsResponse.builder()
                .clusterId(id)
                .clusterName(cluster.getName())
                .status(cluster.getStatus().name())
                .timestamp(Instant.now())
                .totalNodes(nodes.size())
                .runningNodes(runningCount)
                .avgCpuPercent(runningCount > 0 ? Math.round((totalCpu / runningCount) * 100.0) / 100.0 : 0)
                .totalMemoryUsage(totalMemUsage)
                .totalMemoryLimit(totalMemLimit)
                .avgMemoryPercent(
                        totalMemLimit > 0 ? Math.round((double) totalMemUsage / totalMemLimit * 10000.0) / 100.0 : 0)
                .totalNetworkRx(totalNetRx)
                .totalNetworkTx(totalNetTx)
                .totalBlockRead(totalBlockRead)
                .totalBlockWrite(totalBlockWrite)
                .nodeMetrics(nodeStats)
                .queriesPerSecond((Double) mysqlMetrics.getOrDefault("queriesPerSecond", 0.0))
                .activeConnections((Integer) mysqlMetrics.getOrDefault("connections", 0))
                .replicationLagSeconds((Integer) mysqlMetrics.getOrDefault("replicationLag", 0))
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Start a stopped cluster")
    public ResponseEntity<ApiResponse<Cluster>> startCluster(@PathVariable String id) {
        Cluster cluster = clusterService.startCluster(id);
        return ResponseEntity.ok(ApiResponse.success(cluster, "Cluster started successfully"));
    }

    @PostMapping("/{id}/stop")
    @Operation(summary = "Stop a running cluster")
    public ResponseEntity<ApiResponse<Cluster>> stopCluster(@PathVariable String id) {
        Cluster cluster = clusterService.stopCluster(id);
        return ResponseEntity.ok(ApiResponse.success(cluster, "Cluster stopped successfully"));
    }

    /**
     * DTO for cluster health response.
     */
    public record ClusterHealthResponse(String clusterId, ClusterStatus status, boolean healthy) {
    }

    /**
     * Extract user ID from Authentication (JWT).
     */
    private String getUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return "anonymous";
    }
}
