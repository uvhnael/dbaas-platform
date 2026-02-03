package com.dbaas.service.cluster;

import com.dbaas.config.CacheConfig;
import com.dbaas.exception.ClusterNotFoundException;
import com.dbaas.filter.CorrelationIdFilter;
import com.dbaas.model.Cluster;
import com.dbaas.model.ClusterStatus;
import com.dbaas.model.dto.ScaleRequest;
import com.dbaas.repository.ClusterRepository;
import com.dbaas.repository.NodeRepository;
import com.dbaas.service.DockerService;
import com.dbaas.service.OrchestratorService;
import com.dbaas.service.ProxySQLService;
import com.dbaas.util.AsyncRetryUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Service responsible for cluster scaling operations - adding or removing
 * replicas.
 * 
 * <p>
 * Extracted from ClusterService to follow Single Responsibility Principle.
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClusterScalingService {

    private final ClusterRepository clusterRepository;
    private final NodeRepository nodeRepository;
    private final DockerService dockerService;
    private final ProxySQLService proxySQLService;
    private final OrchestratorService orchestratorService;
    private final ClusterProvisioningService provisioningService;
    private final ClusterHealthService healthService;
    private final NodePersistenceService nodePersistenceService;
    private final CacheManager cacheManager;
    private final TransactionTemplate transactionTemplate;
    private final AsyncRetryUtils asyncRetryUtils;

    @Qualifier("clusterProvisioningExecutor")
    private final Executor clusterProvisioningExecutor;

    /**
     * Scale cluster by adding or removing replicas.
     * Returns immediately with PROVISIONING status, then scales async.
     */
    @Transactional
    @CacheEvict(value = { CacheConfig.CLUSTER_CACHE, CacheConfig.CLUSTER_LIST_CACHE,
            CacheConfig.DASHBOARD_CACHE }, allEntries = true)
    public Cluster scaleCluster(String clusterId, ScaleRequest request, String userId) {
        String correlationId = CorrelationIdFilter.getCurrentCorrelationId();
        log.info("[{}] Scaling cluster '{}' to {} replicas", correlationId, clusterId, request.getReplicaCount());

        // Validate replica count
        int targetReplicas = request.getReplicaCount();
        if (targetReplicas < 0 || targetReplicas > 10) {
            throw new IllegalArgumentException("Replica count must be between 0 and 10");
        }

        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new ClusterNotFoundException(clusterId));

        // Verify ownership
        if (!cluster.getUserId().equals(userId)) {
            throw new AccessDeniedException("Not authorized to access this cluster");
        }

        int currentReplicas = cluster.getReplicaContainerIds().size();

        if (targetReplicas == currentReplicas) {
            log.info("Cluster '{}' already has {} replicas, no scaling needed", clusterId, targetReplicas);
            return cluster;
        }

        // Save previous status to restore if scaling fails
        ClusterStatus previousStatus = cluster.getStatus();
        cluster.setStatus(ClusterStatus.SCALING);
        cluster = clusterRepository.save(cluster);

        // Get resource configuration
        final int cpuCores = (request.getResources() != null && request.getResources().getCpuCores() != null)
                ? request.getResources().getCpuCores()
                : 2;
        final String memory = (request.getResources() != null && request.getResources().getMemory() != null)
                ? request.getResources().getMemory()
                : "4G";

        // Start async scaling - pass clusterId instead of detached entity
        final String clusterIdForAsync = clusterId;
        CompletableFuture.runAsync(() -> {
            CorrelationIdFilter.setCorrelationId(correlationId);
            scaleClusterAsync(clusterIdForAsync, currentReplicas, targetReplicas, cpuCores, memory, previousStatus);
        }, clusterProvisioningExecutor);

        return cluster;
    }

    /**
     * Async scaling of cluster replicas.
     * Called via CompletableFuture.runAsync() - do NOT add @Async annotation.
     * Reloads cluster from database to avoid stale entity issues.
     * 
     * <p>
     * Follows the same flow pattern as cluster creation:
     * </p>
     * <ul>
     * <li>Phase 1: Initial delay for transaction commit</li>
     * <li>Phase 2: Load cluster and perform scaling</li>
     * <li>Phase 3: Finalize in transaction</li>
     * </ul>
     */
    public void scaleClusterAsync(String clusterId, int currentReplicas, int targetReplicas,
            int cpuCores, String memory, ClusterStatus previousStatus) {

        log.info("Starting async scaling for cluster '{}': {} -> {} replicas",
                clusterId, currentReplicas, targetReplicas);

        // Phase 1: Brief delay for transaction commit using non-blocking delay
        asyncRetryUtils.delay(Duration.ofMillis(500)).join();

        // Phase 2: Load cluster in transaction
        Cluster cluster = transactionTemplate.execute(status -> clusterRepository.findById(clusterId).orElse(null));

        if (cluster == null) {
            log.error("Cluster '{}' not found for scaling", clusterId);
            return;
        }

        try {
            // Phase 3: Perform scaling operations
            if (targetReplicas > currentReplicas) {
                scaleUpReplicas(cluster, currentReplicas, targetReplicas, cpuCores, memory);
            } else {
                scaleDownReplicas(cluster, currentReplicas, targetReplicas);
            }

            // Phase 4: Finalize in transaction
            transactionTemplate.executeWithoutResult(status -> finalizeScalingInternal(cluster, targetReplicas));
            log.info("Cluster '{}' scaled successfully to {} replicas", clusterId, targetReplicas);

        } catch (Exception e) {
            // Handle failure in transaction
            transactionTemplate
                    .executeWithoutResult(status -> handleScalingFailureInternal(cluster, e, previousStatus));
        }
    }

    /**
     * Scale up by adding new replicas.
     * Follows same pattern as cluster creation provisioning.
     */
    private void scaleUpReplicas(Cluster cluster, int currentReplicas, int targetReplicas,
            int cpuCores, String memory) {
        String clusterId = cluster.getId();
        String storage = "10G";

        for (int i = currentReplicas + 1; i <= targetReplicas; i++) {
            final int replicaNum = i;
            log.info("Creating MySQL Replica {} for cluster '{}'", replicaNum, clusterId);

            // Step 1: Create container
            String replicaContainerId = provisioningService.createReplicaContainer(
                    cluster, replicaNum, cpuCores, memory, storage);
            cluster.getReplicaContainerIds().add(replicaContainerId);

            // Step 2: Wait for container to be healthy
            healthService.waitForSingleContainerHealthy(replicaContainerId, 120);
            log.info("MySQL Replica {} container is healthy", replicaNum);

            // Step 3: Add safety delay for MySQL to fully initialize (like create cluster
            // flow)
            asyncRetryUtils.delay(Duration.ofSeconds(10)).join();
            log.info("MySQL Replica {} initialization delay completed", replicaNum);

            // Step 4: Configure replication
            safeExecuteWithRetry("Setup Replication for Replica " + replicaNum,
                    () -> dockerService.setupReplicaReplication(cluster, replicaContainerId), 3, 5000);

            // Step 5: Add to ProxySQL
            safeExecuteWithRetry("Add Replica " + replicaNum + " to ProxySQL",
                    () -> proxySQLService.addReplica(cluster, replicaContainerId), 3, 3000);

            // Step 6: Update node status
            provisioningService.updateNodeStatusToRunning(replicaContainerId);

            // Step 7: Save progress - reload fresh cluster to avoid optimistic locking
            final String replicaId = replicaContainerId;
            transactionTemplate.executeWithoutResult(status -> {
                Cluster freshCluster = clusterRepository.findById(clusterId).orElse(null);
                if (freshCluster != null && !freshCluster.getReplicaContainerIds().contains(replicaId)) {
                    freshCluster.getReplicaContainerIds().add(replicaId);
                    clusterRepository.save(freshCluster);
                }
            });

            log.info("MySQL Replica {} added successfully to cluster '{}'", replicaNum, clusterId);
        }
    }

    /**
     * Scale down by removing replicas.
     * Follows structured cleanup pattern.
     */
    private void scaleDownReplicas(Cluster cluster, int currentReplicas, int targetReplicas) {
        String clusterId = cluster.getId();

        for (int i = currentReplicas; i > targetReplicas; i--) {
            final int replicaNum = i;
            log.info("Removing MySQL Replica {} from cluster '{}'", replicaNum, clusterId);

            // Step 1: Reload cluster to get current state
            Cluster freshCluster = transactionTemplate
                    .execute(status -> clusterRepository.findById(clusterId).orElse(null));

            if (freshCluster == null || freshCluster.getReplicaContainerIds().isEmpty()) {
                log.warn("Cluster '{}' not found or has no replicas to remove", clusterId);
                continue;
            }

            // Step 2: Get the last replica container ID
            int lastIndex = freshCluster.getReplicaContainerIds().size() - 1;
            if (lastIndex < 0) {
                log.warn("No more replicas to remove for cluster '{}'", clusterId);
                break;
            }
            String replicaContainerId = freshCluster.getReplicaContainerIds().get(lastIndex);
            String hostname = "mysql-" + clusterId + "-replica-" + replicaNum;

            // Step 3: Remove from ProxySQL first (graceful removal)
            safeExecute("Remove from ProxySQL",
                    () -> proxySQLService.removeServer(cluster, hostname));

            // Step 4: Brief delay for connections to drain
            asyncRetryUtils.delay(Duration.ofSeconds(2)).join();

            // Step 5: Remove node from database (in separate transaction)
            nodePersistenceService.deleteNodeByContainerId(replicaContainerId);

            // Step 6: Stop and remove container
            final String containerToRemove = replicaContainerId;
            safeExecute("Remove container",
                    () -> dockerService.removeContainer(containerToRemove));

            // Step 7: Unregister from orchestrator
            orchestratorService.unregisterNode(hostname);

            // Step 8: Save progress - reload fresh cluster to avoid optimistic locking
            transactionTemplate.executeWithoutResult(status -> {
                Cluster updatedCluster = clusterRepository.findById(clusterId).orElse(null);
                if (updatedCluster != null) {
                    updatedCluster.getReplicaContainerIds().remove(containerToRemove);
                    clusterRepository.save(updatedCluster);
                }
            });

            log.info("MySQL Replica {} removed successfully from cluster '{}'", replicaNum, clusterId);
        }
    }

    /**
     * Finalize scaling operation (internal - called within transaction).
     * Re-fetches cluster to get fresh version number (prevents optimistic locking
     * failure).
     */
    private void finalizeScalingInternal(Cluster cluster, int targetReplicas) {
        // Re-fetch cluster to get fresh version (avoids
        // OptimisticLockingFailureException)
        Cluster freshCluster = clusterRepository.findById(cluster.getId()).orElse(cluster);
        freshCluster.setReplicaCount(targetReplicas);
        freshCluster.setStatus(ClusterStatus.RUNNING);
        freshCluster.setErrorMessage(null);
        freshCluster.setUpdatedAt(java.time.Instant.now());
        clusterRepository.save(freshCluster);

        evictAllClusterCaches();
        log.info("Cluster '{}' scaling finalized: {} replicas, {} container IDs",
                freshCluster.getId(), targetReplicas, freshCluster.getReplicaContainerIds().size());
    }

    /**
     * Handle scaling failure (internal - called within transaction).
     * Re-fetches cluster to get fresh version number (prevents optimistic locking
     * failure).
     */
    private void handleScalingFailureInternal(Cluster cluster, Exception e, ClusterStatus previousStatus) {
        log.error("Failed to scale cluster '{}': {}", cluster.getId(), e.getMessage(), e);

        // Re-fetch cluster to get fresh version (avoids
        // OptimisticLockingFailureException)
        Cluster freshCluster = clusterRepository.findById(cluster.getId()).orElse(cluster);
        freshCluster.setStatus(previousStatus != null ? previousStatus : ClusterStatus.DEGRADED);
        freshCluster.setErrorMessage("Scaling failed: " + e.getMessage());
        freshCluster.setUpdatedAt(java.time.Instant.now());
        clusterRepository.save(freshCluster);

        evictAllClusterCaches();
    }

    private void evictAllClusterCaches() {
        if (cacheManager.getCache(CacheConfig.CLUSTER_CACHE) != null) {
            cacheManager.getCache(CacheConfig.CLUSTER_CACHE).clear();
        }
        if (cacheManager.getCache(CacheConfig.CLUSTER_LIST_CACHE) != null) {
            cacheManager.getCache(CacheConfig.CLUSTER_LIST_CACHE).clear();
        }
        if (cacheManager.getCache(CacheConfig.DASHBOARD_CACHE) != null) {
            cacheManager.getCache(CacheConfig.DASHBOARD_CACHE).clear();
        }
    }

    private void safeExecute(String actionName, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("Failed to {} (non-fatal): {}", actionName, e.getMessage());
        }
    }

    /**
     * Execute action with retry using non-blocking async pattern.
     * Uses AsyncRetryUtils for better scalability than Thread.sleep().
     */
    private void safeExecuteWithRetry(String actionName, Runnable action, int maxRetries, long delayMs) {
        try {
            asyncRetryUtils.executeWithRetryAsync(
                    actionName,
                    action,
                    maxRetries,
                    Duration.ofMillis(delayMs),
                    clusterProvisioningExecutor).join(); // Block here since the calling context expects synchronous
                                                         // completion
        } catch (Exception e) {
            log.error("{} failed after {} attempts: {}", actionName, maxRetries, e.getMessage());
        }
    }
}
