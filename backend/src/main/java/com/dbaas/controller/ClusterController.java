package com.dbaas.controller;

import com.dbaas.model.dto.ClusterConnectionDTO;
import com.dbaas.model.dto.ClusterResponse;
import com.dbaas.mapper.ClusterMapper;
import com.dbaas.model.Cluster;
import com.dbaas.model.ClusterStatus;
import com.dbaas.model.dto.ApiResponse;
import com.dbaas.util.AuthHelper;
import com.dbaas.model.dto.ClusterMetricsResponse;
import com.dbaas.model.dto.CreateClusterRequest;
import com.dbaas.model.dto.ScaleRequest;
import com.dbaas.service.ClusterService;
import com.dbaas.service.cluster.ClusterMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for cluster management.
 * 
 * <p>
 * Returns ClusterResponse DTOs instead of entities to avoid exposing internal
 * structure.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/clusters")
@RequiredArgsConstructor
@Tag(name = "Clusters", description = "MySQL Cluster Management API")
public class ClusterController {

    private final ClusterService clusterService;
    private final ClusterMetricsService clusterMetricsService;
    private final ClusterMapper clusterMapper;
    private final AuthHelper authHelper;

    @PostMapping
    @Operation(summary = "Create a new cluster")
    public ResponseEntity<ApiResponse<ClusterResponse>> createCluster(
            @Valid @RequestBody CreateClusterRequest request,
            Authentication authentication) {

        String userId = authHelper.getUserId(authentication);
        Cluster cluster = clusterService.createCluster(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(clusterMapper.toResponse(cluster), "Cluster created successfully"));
    }

    @GetMapping
    @Operation(summary = "List all clusters with optional pagination")
    public ResponseEntity<ApiResponse<Object>> listClusters(
            Authentication authentication,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir,
            @Parameter(description = "Enable pagination") @RequestParam(defaultValue = "false") boolean paged) {

        String userId = authHelper.getUserId(authentication);

        if (paged) {
            Sort sort = sortDir.equalsIgnoreCase("asc")
                    ? Sort.by(sortBy).ascending()
                    : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, Math.min(size, 100), sort); // Max 100 per page
            Page<Cluster> clusters = clusterService.listClustersPaged(userId, pageable);
            Page<ClusterResponse> response = clusters.map(clusterMapper::toResponse);
            return ResponseEntity.ok(ApiResponse.success(response));
        } else {
            List<Cluster> clusters = clusterService.listClusters(userId);
            List<ClusterResponse> response = clusterMapper.toResponseList(clusters);
            return ResponseEntity.ok(ApiResponse.success(response));
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get cluster details")
    public ResponseEntity<ApiResponse<ClusterResponse>> getCluster(
            @PathVariable String id,
            Authentication authentication) {
        String userId = authHelper.getUserId(authentication);
        Cluster cluster = clusterService.getCluster(id, userId);
        return ResponseEntity.ok(ApiResponse.success(clusterMapper.toResponse(cluster)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a cluster")
    public ResponseEntity<ApiResponse<ClusterResponse>> deleteCluster(
            @PathVariable String id,
            Authentication authentication) {
        String userId = authHelper.getUserId(authentication);
        Cluster cluster = clusterService.deleteCluster(id, userId);
        return ResponseEntity.ok(ApiResponse.success(clusterMapper.toResponse(cluster), "Cluster deletion started"));
    }

    @PostMapping("/{id}/scale")
    @Operation(summary = "Scale cluster replicas")
    public ResponseEntity<ApiResponse<ClusterResponse>> scaleCluster(
            @PathVariable String id,
            @Valid @RequestBody ScaleRequest request,
            Authentication authentication) {
        String userId = authHelper.getUserId(authentication);
        Cluster cluster = clusterService.scaleCluster(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success(clusterMapper.toResponse(cluster), "Cluster scaled successfully"));
    }

    @GetMapping("/{id}/connection")
    @Operation(summary = "Get cluster connection information (host, ports, credentials)")
    public ResponseEntity<ApiResponse<ClusterConnectionDTO>> getClusterConnection(
            @PathVariable String id,
            Authentication authentication) {
        String userId = authHelper.getUserId(authentication);
        ClusterConnectionDTO connection = clusterService.getClusterConnection(id, userId);
        return ResponseEntity.ok(ApiResponse.success(connection));
    }

    @GetMapping("/{id}/health")
    @Operation(summary = "Get cluster health status")
    public ResponseEntity<ApiResponse<ClusterHealthResponse>> getClusterHealth(
            @PathVariable String id,
            Authentication authentication) {
        String userId = authHelper.getUserId(authentication);
        ClusterStatus status = clusterService.getClusterHealth(id, userId);
        ClusterHealthResponse health = new ClusterHealthResponse(
                id, status, status == ClusterStatus.RUNNING || status == ClusterStatus.HEALTHY);
        return ResponseEntity.ok(ApiResponse.success(health));
    }

    @GetMapping("/{id}/metrics")
    @Operation(summary = "Get aggregated metrics for all nodes in a cluster")
    public ResponseEntity<ApiResponse<ClusterMetricsResponse>> getClusterMetrics(
            @PathVariable String id,
            Authentication authentication) {
        String userId = authHelper.getUserId(authentication);
        ClusterMetricsResponse response = clusterMetricsService.getClusterMetrics(id, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Start a stopped cluster")
    public ResponseEntity<ApiResponse<ClusterResponse>> startCluster(
            @PathVariable String id,
            Authentication authentication) {
        String userId = authHelper.getUserId(authentication);
        Cluster cluster = clusterService.startCluster(id, userId);
        return ResponseEntity
                .ok(ApiResponse.success(clusterMapper.toResponse(cluster), "Cluster started successfully"));
    }

    @PostMapping("/{id}/stop")
    @Operation(summary = "Stop a running cluster")
    public ResponseEntity<ApiResponse<ClusterResponse>> stopCluster(
            @PathVariable String id,
            Authentication authentication) {
        String userId = authHelper.getUserId(authentication);
        Cluster cluster = clusterService.stopCluster(id, userId);
        return ResponseEntity
                .ok(ApiResponse.success(clusterMapper.toResponse(cluster), "Cluster stopped successfully"));
    }

    /**
     * DTO for cluster health response.
     */
    public record ClusterHealthResponse(String clusterId, ClusterStatus status, boolean healthy) {
    }
}
