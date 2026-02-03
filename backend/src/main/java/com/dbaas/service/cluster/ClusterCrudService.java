package com.dbaas.service.cluster;

import com.dbaas.config.CacheConfig;
import com.dbaas.model.dto.ClusterConnectionDTO;
import com.dbaas.exception.ClusterNotFoundException;
import com.dbaas.filter.CorrelationIdFilter;
import org.springframework.security.access.AccessDeniedException;
import com.dbaas.model.Cluster;
import com.dbaas.model.ClusterStatus;
import com.dbaas.model.Node;
import com.dbaas.model.dto.CreateClusterRequest;
import com.dbaas.repository.ClusterRepository;
import com.dbaas.repository.NodeRepository;
import com.dbaas.service.DockerService;
import com.dbaas.service.OrchestratorService;
import com.dbaas.service.ProxySQLService;
import com.dbaas.service.SecretService;
import com.dbaas.util.AsyncRetryUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Main service for cluster CRUD operations.
 * Acts as a facade that delegates complex operations to specialized services.
 * 
 * <p>
 * Refactored from original 1092-line ClusterService to follow SRP.
 * </p>
 * 
 * <p>
 * Delegated responsibilities:
 * </p>
 * <ul>
 * <li>{@link ClusterProvisioningService} - Container creation and cleanup</li>
 * <li>{@link ClusterScalingService} - Scale up/down operations</li>
 * <li>{@link ClusterHealthService} - Health monitoring</li>
 * <li>{@link ClusterMetricsService} - Metrics aggregation</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClusterCrudService {

    private final ClusterRepository clusterRepository;
    private final NodeRepository nodeRepository;
    private final DockerService dockerService;
    private final ProxySQLService proxySQLService;
    private final OrchestratorService orchestratorService;
    private final SecretService secretService;
    private final CacheManager cacheManager;
    private final TransactionTemplate transactionTemplate;
    private final AsyncRetryUtils asyncRetryUtils;

    // Delegated services
    private final ClusterProvisioningService provisioningService;
    private final ClusterHealthService healthService;

    @Qualifier("clusterProvisioningExecutor")
    private final Executor clusterProvisioningExecutor;

    // ========================================================================
    // CRUD OPERATIONS
    // ========================================================================

    /**
     * Create a new MySQL HA cluster.
     * Returns immediately with PROVISIONING status, then provisions async.
     */
    @Transactional
    @CacheEvict(value = { CacheConfig.CLUSTER_LIST_CACHE, CacheConfig.DASHBOARD_CACHE }, allEntries = true)
    public Cluster createCluster(CreateClusterRequest request, String userId) {
        String correlationId = CorrelationIdFilter.getCurrentCorrelationId();
        log.info("[{}] Creating cluster '{}' for user '{}'", correlationId, request.getName(), userId);

        String clusterId = UUID.randomUUID().toString().substring(0, 8);

        // Store node configs for async provisioning
        provisioningService.storeProvisioningConfig(clusterId, request);

        // Generate credentials
        String appPassword = secretService.generateAppUserPassword(clusterId);
        String rootPassword = secretService.generateMySQLRootPassword(clusterId);

        // Create cluster entity
        Cluster cluster = Cluster.builder()
                .id(clusterId)
                .name(request.getName())
                .userId(userId)
                .mysqlVersion(request.getMysqlVersion())
                .replicaCount(request.getReplicaCount())
                .status(ClusterStatus.PROVISIONING)
                .description(request.getDescription())
                .enableOrchestrator(request.getFeatures().isEnableOrchestrator())
                .enableBackup(request.getFeatures().isEnableBackup())
                .dbUser("app_user")
                .dbPassword(secretService.encryptSecret(appPassword))
                .rootPassword(secretService.encryptSecret(rootPassword))
                .createdAt(Instant.now())
                .build();

        cluster = clusterRepository.save(cluster);
        log.info("[{}] Cluster '{}' saved with PROVISIONING status", correlationId, clusterId);

        // Start async provisioning
        final String finalClusterId = clusterId;
        CompletableFuture.runAsync(() -> {
            CorrelationIdFilter.setCorrelationId(correlationId);
            provisionClusterAsync(finalClusterId);
        }, clusterProvisioningExecutor);

        return cluster;
    }

    /**
     * Async provisioning of cluster infrastructure.
     * Called via CompletableFuture.runAsync() - do NOT add @Async annotation.
     */
    public void provisionClusterAsync(String clusterId) {
        log.info("╔══════════════════════════════════════════════════════════════════╗");
        log.info("║  STARTING ASYNC PROVISIONING FOR CLUSTER: {}                  ║", clusterId);
        log.info("╚══════════════════════════════════════════════════════════════════╝");

        // Brief delay for transaction commit using non-blocking delay
        log.info("[{}] Waiting 500ms for transaction commit...", clusterId);
        asyncRetryUtils.delay(Duration.ofMillis(500)).join();

        // Load cluster in transaction
        log.info("[{}] Loading cluster from database...", clusterId);
        Cluster cluster = transactionTemplate.execute(status -> clusterRepository.findById(clusterId).orElse(null));

        if (cluster == null) {
            log.error("[{}] ✗ Cluster not found for provisioning - ABORTING", clusterId);
            return;
        }
        log.info("[{}] ✓ Cluster loaded: name='{}', mysqlVersion='{}', replicaCount={}", 
                clusterId, cluster.getName(), cluster.getMysqlVersion(), cluster.getReplicaCount());

        try {
            // Phase 1: Create containers
            log.info("========== PHASE 1: CREATE CONTAINERS FOR CLUSTER '{}' ==========", clusterId);
            provisioningService.createContainersWithRetry(cluster);
            log.info("[{}] ✓ Phase 1 completed - All containers created", clusterId);

            // Phase 2: Wait for health
            log.info("========== PHASE 2: WAIT FOR CONTAINERS HEALTHY ==========");
            healthService.waitForContainersHealthy(cluster, 300);
            log.info("[{}] ✓ Phase 2 completed - All containers are healthy", clusterId);

            // Add safety delay for MySQL to fully initialize (non-blocking)
            log.info("[{}] Waiting 30 seconds for MySQL to fully initialize...", clusterId);
            asyncRetryUtils.delay(Duration.ofSeconds(30)).join();
            log.info("[{}] ✓ MySQL initialization delay completed", clusterId);

            // Phase 3: Configure services
            configureServices(cluster);

            // Phase 4: Finalize (in transaction)
            log.info("========== PHASE 4: FINALIZE PROVISIONING ==========");
            transactionTemplate.executeWithoutResult(status -> finalizeProvisioning(cluster));

            log.info("╔══════════════════════════════════════════════════════════════════╗");
            log.info("║  ✓ CLUSTER '{}' PROVISIONED SUCCESSFULLY!                     ║", clusterId);
            log.info("║  - Master: mysql-{}-master                               ║", clusterId);
            log.info("║  - Replicas: {} nodes registered to ProxySQL                     ║", cluster.getReplicaContainerIds().size());
            log.info("║  - ProxySQL: proxysql-{} (read/write splitting active)    ║", clusterId);
            log.info("╚══════════════════════════════════════════════════════════════════╝");

        } catch (Exception e) {
            log.error("[{}] ✗ PROVISIONING FAILED: {}", clusterId, e.getMessage());
            // Handle failure in transaction
            transactionTemplate.executeWithoutResult(status -> handleProvisioningFailure(cluster, e));
        }
    }

    /**
     * Get cluster by ID (internal use only - no ownership check).
     * Use getCluster(clusterId, userId) for user-facing operations.
     */
    @Cacheable(value = CacheConfig.CLUSTER_CACHE, key = "#clusterId")
    public Cluster getClusterInternal(String clusterId) {
        return clusterRepository.findById(clusterId)
                .orElseThrow(() -> new ClusterNotFoundException(clusterId));
    }

    /**
     * Get cluster by ID with ownership verification.
     * 
     * @throws AccessDeniedException if cluster doesn't belong to user
     */
    public Cluster getCluster(String clusterId, String userId) {
        Cluster cluster = getClusterInternal(clusterId);
        verifyOwnership(cluster, userId);
        return cluster;
    }

    /**
     * Verify that cluster belongs to user.
     * 
     * @throws AccessDeniedException if not authorized
     */
    private void verifyOwnership(Cluster cluster, String userId) {
        if (!cluster.getUserId().equals(userId)) {
            log.warn("User '{}' attempted to access cluster '{}' owned by '{}'",
                    userId, cluster.getId(), cluster.getUserId());
            throw new AccessDeniedException("Not authorized to access this cluster");
        }
    }

    /**
     * Get cluster connection information.
     */
    public ClusterConnectionDTO getClusterConnection(String clusterId, String userId) {
        Cluster cluster = getCluster(clusterId, userId);

        String proxyContainerName = "proxysql-" + clusterId;
        String dockerNetwork = "dbaas-network";

        String password = cluster.getDbPassword() != null
                ? secretService.decryptSecret(cluster.getDbPassword())
                : secretService.generateAppUserPassword(clusterId);
        String rootPassword = cluster.getRootPassword() != null
                ? secretService.decryptSecret(cluster.getRootPassword())
                : secretService.generateMySQLRootPassword(clusterId);

        String username = cluster.getDbUser() != null ? cluster.getDbUser() : "app_user";

        Integer port = cluster.getProxyPort();
        if (port == null) {
            port = dockerService.getPublishedPort(proxyContainerName, 6033);
            if (port == null) {
                port = 6033;
            }
        }

        // Connection string without password for security - password returned in
        // separate field
        String connectionString = String.format(
                "mysql://%s@%s:%d", username, proxyContainerName, port);

        return ClusterConnectionDTO.builder()
                .clusterId(clusterId)
                .clusterName(cluster.getName())
                .host(proxyContainerName)
                .port(port)
                .username(username)
                .password(password)
                .rootPassword(rootPassword)
                .connectionString(connectionString)
                .proxyContainerName(proxyContainerName)
                .dockerNetwork(dockerNetwork)
                .build();
    }

    /**
     * List all clusters for a user.
     */
    @Cacheable(value = CacheConfig.CLUSTER_LIST_CACHE, key = "#userId")
    public List<Cluster> listClusters(String userId) {
        return clusterRepository.findByUserId(userId);
    }

    /**
     * List clusters with pagination.
     */
    public Page<Cluster> listClustersPaged(String userId, Pageable pageable) {
        return clusterRepository.findByUserId(userId, pageable);
    }

    /**
     * Delete a cluster.
     */
    @Transactional
    @CacheEvict(value = { CacheConfig.CLUSTER_CACHE, CacheConfig.CLUSTER_LIST_CACHE,
            CacheConfig.DASHBOARD_CACHE }, allEntries = true)
    public Cluster deleteCluster(String clusterId, String userId) {
        String correlationId = CorrelationIdFilter.getCurrentCorrelationId();
        log.info("[{}] Deleting cluster '{}'", correlationId, clusterId);

        Cluster cluster = getCluster(clusterId, userId);
        cluster.setStatus(ClusterStatus.DELETING);
        cluster = clusterRepository.save(cluster);

        final String finalClusterId = clusterId;
        CompletableFuture.runAsync(() -> {
            CorrelationIdFilter.setCorrelationId(correlationId);
            deleteClusterAsync(finalClusterId);
        }, clusterProvisioningExecutor);

        return cluster;
    }

    /**
     * Async deletion of cluster resources.
     * Called via CompletableFuture.runAsync() - do NOT add @Async annotation.
     */
    public void deleteClusterAsync(String clusterId) {
        log.info("Starting async deletion for cluster '{}'", clusterId);

        // Load cluster in transaction
        Cluster cluster = transactionTemplate.execute(status -> clusterRepository.findById(clusterId).orElse(null));

        if (cluster == null) {
            log.error("Cluster '{}' not found for deletion", clusterId);
            return;
        }

        try {
            // External service calls (non-transactional)
            orchestratorService.unregisterCluster(cluster);
            provisioningService.cleanupCluster(cluster);

            // Database operations in transaction
            // Reload cluster to avoid optimistic locking issues
            transactionTemplate.executeWithoutResult(status -> {
                Cluster freshCluster = clusterRepository.findById(clusterId).orElse(null);
                if (freshCluster != null) {
                    List<Node> nodes = nodeRepository.findByClusterId(clusterId);
                    nodeRepository.deleteAll(nodes);
                    clusterRepository.delete(freshCluster);
                }
            });

            evictAllClusterCaches();
            log.info("Cluster '{}' deleted successfully", clusterId);

        } catch (Exception e) {
            log.error("Failed to delete cluster '{}'", clusterId, e);

            // Update failure status in transaction
            transactionTemplate.executeWithoutResult(status -> {
                Cluster freshCluster = clusterRepository.findById(clusterId).orElse(null);
                if (freshCluster != null) {
                    freshCluster.setStatus(ClusterStatus.FAILED);
                    freshCluster.setErrorMessage("Delete failed: " + e.getMessage());
                    clusterRepository.save(freshCluster);
                }
            });
            evictAllClusterCaches();
        }
    }

    /**
     * Start a stopped cluster.
     */
    @Transactional
    @CacheEvict(value = { CacheConfig.CLUSTER_CACHE, CacheConfig.CLUSTER_LIST_CACHE,
            CacheConfig.DASHBOARD_CACHE }, allEntries = true)
    public Cluster startCluster(String clusterId, String userId) {
        log.info("Starting cluster '{}'", clusterId);
        Cluster cluster = getCluster(clusterId, userId);

        if (cluster.getStatus() != ClusterStatus.STOPPED) {
            throw new IllegalStateException("Cluster is not in STOPPED state");
        }

        ClusterStatus newStatus = ClusterStatus.RUNNING;
        String errorMessage = null;

        try {
            dockerService.startContainer(cluster.getMasterContainerId());
            for (String replicaId : cluster.getReplicaContainerIds()) {
                dockerService.startContainer(replicaId);
            }
            dockerService.startContainer(cluster.getProxySqlContainerId());

            log.info("Cluster '{}' started successfully", clusterId);

        } catch (Exception e) {
            log.error("Failed to start cluster '{}'", clusterId, e);
            newStatus = ClusterStatus.FAILED;
            errorMessage = "Start failed: " + e.getMessage();
        }

        // Reload fresh cluster to avoid optimistic locking
        Cluster freshCluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new ClusterNotFoundException(clusterId));
        freshCluster.setStatus(newStatus);
        freshCluster.setErrorMessage(errorMessage);
        freshCluster.setUpdatedAt(Instant.now());
        return clusterRepository.save(freshCluster);
    }

    /**
     * Stop a running cluster.
     */
    @Transactional
    @CacheEvict(value = { CacheConfig.CLUSTER_CACHE, CacheConfig.CLUSTER_LIST_CACHE,
            CacheConfig.DASHBOARD_CACHE }, allEntries = true)
    public Cluster stopCluster(String clusterId, String userId) {
        log.info("Stopping cluster '{}'", clusterId);
        Cluster cluster = getCluster(clusterId, userId);

        if (cluster.getStatus() != ClusterStatus.RUNNING) {
            throw new IllegalStateException("Cluster is not in RUNNING state");
        }

        ClusterStatus newStatus = ClusterStatus.STOPPED;
        String errorMessage = null;

        try {
            dockerService.stopContainer(cluster.getProxySqlContainerId());
            for (String replicaId : cluster.getReplicaContainerIds()) {
                dockerService.stopContainer(replicaId);
            }
            dockerService.stopContainer(cluster.getMasterContainerId());

            log.info("Cluster '{}' stopped successfully", clusterId);

        } catch (Exception e) {
            log.error("Failed to stop cluster '{}'", clusterId, e);
            errorMessage = "Stop failed: " + e.getMessage();
        }

        // Reload fresh cluster to avoid optimistic locking
        Cluster freshCluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new ClusterNotFoundException(clusterId));
        freshCluster.setStatus(newStatus);
        if (errorMessage != null) {
            freshCluster.setErrorMessage(errorMessage);
        }
        freshCluster.setUpdatedAt(Instant.now());

        return clusterRepository.save(freshCluster);
    }

    /**
     * Get cluster health status (internal use).
     */
    public ClusterStatus getClusterHealth(String clusterId) {
        return healthService.getClusterHealthInternal(clusterId);
    }

    // ========================================================================
    // PRIVATE METHODS
    // ========================================================================

    private void configureServices(Cluster cluster) {
        String clusterId = cluster.getId();
        String masterContainer = "mysql-" + clusterId + "-master";
        
        log.info("========== PHASE 3: CONFIGURE SERVICES FOR CLUSTER '{}' ==========", clusterId);
        
        // Step 1: Setup Primary
        log.info("[{}] Step 1/4: Setting up MySQL Primary (Master: {})", clusterId, masterContainer);
        safeExecuteWithRetry("Setup Primary",
                () -> dockerService.setupPrimary(cluster), 3, 5000);
        log.info("[{}] ✓ MySQL Primary setup completed for master: {}", clusterId, masterContainer);

        // Step 2: Setup Replication
        log.info("[{}] Step 2/4: Setting up MySQL Replication ({} replicas)", clusterId, cluster.getReplicaContainerIds().size());
        for (int i = 1; i <= cluster.getReplicaContainerIds().size(); i++) {
            log.info("[{}]   - Replica {}: mysql-{}-replica-{}", clusterId, i, clusterId, i);
        }
        safeExecuteWithRetry("Setup Replication",
                () -> dockerService.setupReplication(cluster), 3, 5000);
        log.info("[{}] ✓ MySQL Replication setup completed for {} replicas", clusterId, cluster.getReplicaContainerIds().size());

        // Step 3: Configure ProxySQL
        log.info("[{}] Step 3/4: Configuring ProxySQL for read/write splitting", clusterId);
        log.info("[{}]   - Registering master '{}' to WRITE hostgroup (10)", clusterId, masterContainer);
        for (int i = 1; i <= cluster.getReplicaContainerIds().size(); i++) {
            log.info("[{}]   - Registering replica 'mysql-{}-replica-{}' to READ hostgroup (20)", clusterId, clusterId, i);
        }
        safeExecuteWithRetry("Configure ProxySQL",
                () -> proxySQLService.configureCluster(cluster), 3, 3000);
        log.info("[{}] ✓ ProxySQL configuration completed - Master and {} replicas registered", clusterId, cluster.getReplicaContainerIds().size());

        // Step 4: Register with Orchestrator
        log.info("[{}] Step 4/4: Registering cluster with Orchestrator for HA monitoring", clusterId);
        safeExecute("Register Orchestrator",
                () -> orchestratorService.registerCluster(cluster));
        log.info("[{}] ✓ Orchestrator registration completed", clusterId);
        
        log.info("========== PHASE 3 COMPLETED: ALL SERVICES CONFIGURED FOR CLUSTER '{}' ==========", clusterId);
    }

    /**
     * Finalize provisioning - marks cluster as RUNNING.
     * Re-fetches cluster to get fresh version number (prevents optimistic locking
     * failure).
     */
    private void finalizeProvisioning(Cluster cluster) {
        // Re-fetch cluster to get fresh version (avoids
        // OptimisticLockingFailureException)
        Cluster freshCluster = clusterRepository.findById(cluster.getId())
                .orElseThrow(() -> new ClusterNotFoundException(cluster.getId()));

        freshCluster.setStatus(ClusterStatus.RUNNING);
        freshCluster.setUpdatedAt(Instant.now());
        // Copy container IDs from provisioning
        freshCluster.setMasterContainerId(cluster.getMasterContainerId());
        freshCluster.setProxySqlContainerId(cluster.getProxySqlContainerId());
        freshCluster.setReplicaContainerIds(cluster.getReplicaContainerIds());
        freshCluster.setNetworkId(cluster.getNetworkId());

        clusterRepository.save(freshCluster);

        healthService.markAllNodesRunning(cluster.getId());
        evictAllClusterCaches();

        log.info("Cluster '{}' marked as RUNNING", cluster.getId());
    }

    /**
     * Handle provisioning failure - marks cluster as FAILED and attempts cleanup.
     * Re-fetches cluster to get fresh version number (prevents optimistic locking
     * failure).
     */
    private void handleProvisioningFailure(Cluster cluster, Exception e) {
        log.error("Failed to provision cluster '{}': {}", cluster.getId(), e.getMessage(), e);

        // Re-fetch cluster to get fresh version (avoids
        // OptimisticLockingFailureException)
        Cluster freshCluster = clusterRepository.findById(cluster.getId())
                .orElseThrow(() -> new ClusterNotFoundException(cluster.getId()));

        freshCluster.setStatus(ClusterStatus.FAILED);
        freshCluster.setErrorMessage(e.getMessage());
        freshCluster.setUpdatedAt(Instant.now());
        clusterRepository.save(freshCluster);

        evictAllClusterCaches();

        try {
            provisioningService.cleanupCluster(cluster);
        } catch (Exception cleanupException) {
            log.warn("Cleanup failed for cluster '{}': {}", cluster.getId(), cleanupException.getMessage());
        }
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
