package com.dbaas.controller;

import com.dbaas.model.Node;
import com.dbaas.model.NodeRole;
import com.dbaas.model.dto.ApiResponse;
import com.dbaas.model.dto.ClusterLogsResponse;
import com.dbaas.model.dto.NodeLogsResponse;
import com.dbaas.model.dto.NodeStatsResponse;
import com.dbaas.repository.NodeRepository;
import com.dbaas.service.DockerService;
import com.dbaas.service.MonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for node management.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Nodes", description = "MySQL Node Management API")
public class NodeController {

    private final NodeRepository nodeRepository;
    private final DockerService dockerService;
    private final MonitoringService monitoringService;

    @GetMapping("/clusters/{clusterId}/nodes")
    @Operation(summary = "List all nodes in a cluster")
    public ResponseEntity<ApiResponse<List<Node>>> listNodes(@PathVariable String clusterId) {
        List<Node> nodes = nodeRepository.findByClusterId(clusterId);
        return ResponseEntity.ok(ApiResponse.success(nodes));
    }

    @GetMapping("/nodes/{nodeId}")
    @Operation(summary = "Get node details")
    public ResponseEntity<ApiResponse<Node>> getNode(@PathVariable String nodeId) {
        Node node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new RuntimeException("Node not found: " + nodeId));
        return ResponseEntity.ok(ApiResponse.success(node));
    }

    @GetMapping("/nodes/{nodeId}/stats")
    @Operation(summary = "Get node container statistics (CPU, memory, network, disk I/O)")
    public ResponseEntity<ApiResponse<NodeStatsResponse>> getNodeStats(@PathVariable String nodeId) {
        Node node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new RuntimeException("Node not found: " + nodeId));

        NodeStatsResponse stats = dockerService.getContainerStats(node.getContainerName(), nodeId);

        // Add node role and replication lag for replica nodes
        stats.setNodeRole(node.getRole() != null ? node.getRole().name() : null);
        if (node.getRole() == NodeRole.REPLICA) {
            stats.setReplicationLagSeconds(
                    monitoringService.getNodeReplicationLag(nodeId, node.getContainerName()));
        }

        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/clusters/{clusterId}/nodes/stats")
    @Operation(summary = "Get stats for all nodes in a cluster")
    public ResponseEntity<ApiResponse<java.util.List<NodeStatsResponse>>> getAllNodesStats(
            @PathVariable String clusterId) {

        java.util.List<Node> nodes = nodeRepository.findByClusterId(clusterId);
        java.util.List<NodeStatsResponse> allStats = new java.util.ArrayList<>();

        for (Node node : nodes) {
            try {
                NodeStatsResponse stats = dockerService.getContainerStats(
                        node.getContainerName(), node.getId());

                // Add node role and replication lag for replica nodes
                stats.setNodeRole(node.getRole() != null ? node.getRole().name() : null);
                if (node.getRole() == NodeRole.REPLICA) {
                    stats.setReplicationLagSeconds(
                            monitoringService.getNodeReplicationLag(node.getId(), node.getContainerName()));
                }

                allStats.add(stats);
            } catch (Exception e) {
                allStats.add(NodeStatsResponse.builder()
                        .nodeId(node.getId())
                        .containerName(node.getContainerName())
                        .nodeRole(node.getRole() != null ? node.getRole().name() : null)
                        .timestamp(java.time.Instant.now())
                        .running(false)
                        .state("error")
                        .build());
            }
        }

        return ResponseEntity.ok(ApiResponse.success(allStats));
    }

    @GetMapping("/nodes/{nodeId}/logs")
    @Operation(summary = "Get node container logs")
    public ResponseEntity<ApiResponse<NodeLogsResponse>> getNodeLogs(
            @PathVariable String nodeId,
            @Parameter(description = "Number of log lines to retrieve") @RequestParam(defaultValue = "100") int lines,
            @Parameter(description = "Include timestamps in log output") @RequestParam(defaultValue = "false") boolean timestamps) {

        Node node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new RuntimeException("Node not found: " + nodeId));

        String logs = dockerService.getContainerLogs(node.getContainerName(), lines, timestamps);
        return ResponseEntity.ok(ApiResponse.success(new NodeLogsResponse(nodeId, node.getContainerName(), logs)));
    }

    @GetMapping("/clusters/{clusterId}/logs")
    @Operation(summary = "Get all cluster container logs")
    public ResponseEntity<ApiResponse<ClusterLogsResponse>> getClusterLogs(
            @PathVariable String clusterId,
            @Parameter(description = "Number of log lines per node") @RequestParam(defaultValue = "50") int lines,
            @Parameter(description = "Include timestamps in log output") @RequestParam(defaultValue = "false") boolean timestamps) {

        List<Node> nodes = nodeRepository.findByClusterId(clusterId);
        List<NodeLogsResponse> nodeLogs = nodes.stream()
                .map(node -> new NodeLogsResponse(
                        node.getId(),
                        node.getContainerName(),
                        dockerService.getContainerLogs(node.getContainerName(), lines, timestamps)))
                .toList();

        return ResponseEntity.ok(ApiResponse.success(new ClusterLogsResponse(clusterId, nodeLogs)));
    }
}
