package com.dbaas.service;

import com.dbaas.model.dto.ClusterConnectionDTO;
import com.dbaas.model.Cluster;
import com.dbaas.model.ClusterStatus;
import com.dbaas.model.dto.CreateClusterRequest;
import com.dbaas.model.dto.ScaleRequest;
import com.dbaas.service.cluster.ClusterCrudService;
import com.dbaas.service.cluster.ClusterHealthService;
import com.dbaas.service.cluster.ClusterScalingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Facade service for cluster operations.
 * 
 * <p>
 * This is a thin facade that delegates to specialized services:
 * </p>
 * <ul>
 * <li>{@link ClusterCrudService} - CRUD operations (create, read, update,
 * delete)</li>
 * <li>{@link ClusterScalingService} - Scale up/down operations</li>
 * <li>{@link ClusterHealthService} - Health monitoring</li>
 * </ul>
 * 
 * <p>
 * Refactored from original 1092-line God Service to follow Single
 * Responsibility Principle.
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClusterService {

    private final ClusterCrudService clusterCrudService;
    private final ClusterScalingService clusterScalingService;
    private final ClusterHealthService clusterHealthService;

    // ========================================================================
    // CRUD OPERATIONS - Delegated to ClusterCrudService
    // ========================================================================

    /**
     * Create a new MySQL HA cluster.
     * Returns immediately with PROVISIONING status, then provisions async.
     */
    public Cluster createCluster(CreateClusterRequest request, String userId) {
        return clusterCrudService.createCluster(request, userId);
    }

    /**
     * Get cluster by ID with ownership verification.
     */
    public Cluster getCluster(String clusterId, String userId) {
        return clusterCrudService.getCluster(clusterId, userId);
    }

    /**
     * Get cluster by ID (internal/system use only - no ownership check).
     * Use for webhooks and system-level operations.
     */
    public Cluster getClusterInternal(String clusterId) {
        return clusterCrudService.getClusterInternal(clusterId);
    }

    /**
     * Get cluster connection information.
     */
    public ClusterConnectionDTO getClusterConnection(String clusterId, String userId) {
        return clusterCrudService.getClusterConnection(clusterId, userId);
    }

    /**
     * List all clusters for a user.
     */
    public List<Cluster> listClusters(String userId) {
        return clusterCrudService.listClusters(userId);
    }

    /**
     * List clusters with pagination.
     */
    public Page<Cluster> listClustersPaged(String userId, Pageable pageable) {
        return clusterCrudService.listClustersPaged(userId, pageable);
    }

    /**
     * Delete a cluster.
     */
    public Cluster deleteCluster(String clusterId, String userId) {
        return clusterCrudService.deleteCluster(clusterId, userId);
    }

    /**
     * Start a stopped cluster.
     */
    public Cluster startCluster(String clusterId, String userId) {
        return clusterCrudService.startCluster(clusterId, userId);
    }

    /**
     * Stop a running cluster.
     */
    public Cluster stopCluster(String clusterId, String userId) {
        return clusterCrudService.stopCluster(clusterId, userId);
    }

    // ========================================================================
    // SCALING OPERATIONS - Delegated to ClusterScalingService
    // ========================================================================

    /**
     * Scale cluster by adding or removing replicas.
     */
    public Cluster scaleCluster(String clusterId, ScaleRequest request, String userId) {
        return clusterScalingService.scaleCluster(clusterId, request, userId);
    }

    // ========================================================================
    // HEALTH OPERATIONS - Delegated to ClusterHealthService
    // ========================================================================

    /**
     * Get cluster health status.
     */
    public ClusterStatus getClusterHealth(String clusterId, String userId) {
        return clusterHealthService.getClusterHealth(clusterId, userId);
    }
}
