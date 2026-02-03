package com.dbaas.controller;

import com.dbaas.model.Node;
import com.dbaas.model.dto.ApiResponse;
import com.dbaas.model.dto.ClusterLogsResponse;
import com.dbaas.model.dto.NodeLogsResponse;
import com.dbaas.model.dto.NodeStatsResponse;
import com.dbaas.service.NodeService;
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

    private final NodeService nodeService;

    @GetMapping("/clusters/{clusterId}/nodes")
    @Operation(summary = "List all nodes in a cluster")
    public ResponseEntity<ApiResponse<List<Node>>> listNodes(@PathVariable String clusterId) {
        List<Node> nodes = nodeService.listNodes(clusterId);
        return ResponseEntity.ok(ApiResponse.success(nodes));
    }

    @GetMapping("/nodes/{nodeId}")
    @Operation(summary = "Get node details")
    public ResponseEntity<ApiResponse<Node>> getNode(@PathVariable String nodeId) {
        Node node = nodeService.getNode(nodeId);
        return ResponseEntity.ok(ApiResponse.success(node));
    }

    @GetMapping("/nodes/{nodeId}/stats")
    @Operation(summary = "Get node container statistics (CPU, memory, network, disk I/O)")
    public ResponseEntity<ApiResponse<NodeStatsResponse>> getNodeStats(@PathVariable String nodeId) {
        NodeStatsResponse stats = nodeService.getNodeStats(nodeId);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/clusters/{clusterId}/nodes/stats")
    @Operation(summary = "Get stats for all nodes in a cluster")
    public ResponseEntity<ApiResponse<List<NodeStatsResponse>>> getAllNodesStats(
            @PathVariable String clusterId) {
        List<NodeStatsResponse> allStats = nodeService.getAllNodesStats(clusterId);
        return ResponseEntity.ok(ApiResponse.success(allStats));
    }

    @GetMapping("/nodes/{nodeId}/logs")
    @Operation(summary = "Get node container logs")
    public ResponseEntity<ApiResponse<NodeLogsResponse>> getNodeLogs(
            @PathVariable String nodeId,
            @Parameter(description = "Number of log lines to retrieve") @RequestParam(defaultValue = "100") int lines,
            @Parameter(description = "Include timestamps in log output") @RequestParam(defaultValue = "false") boolean timestamps) {
        NodeLogsResponse logs = nodeService.getNodeLogs(nodeId, lines, timestamps);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    @GetMapping("/clusters/{clusterId}/logs")
    @Operation(summary = "Get all cluster container logs")
    public ResponseEntity<ApiResponse<ClusterLogsResponse>> getClusterLogs(
            @PathVariable String clusterId,
            @Parameter(description = "Number of log lines per node") @RequestParam(defaultValue = "50") int lines,
            @Parameter(description = "Include timestamps in log output") @RequestParam(defaultValue = "false") boolean timestamps) {
        ClusterLogsResponse logs = nodeService.getClusterLogs(clusterId, lines, timestamps);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
}
