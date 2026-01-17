package com.dbaas.service;

import com.dbaas.model.Cluster;
import com.dbaas.model.ClusterStatus;
import com.dbaas.model.Node;
import com.dbaas.model.NodeRole;
import com.dbaas.model.NodeStatus;
import com.dbaas.model.dto.CreateClusterRequest;
import com.dbaas.model.dto.ScaleRequest;
import com.dbaas.repository.ClusterRepository;
import com.dbaas.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing MySQL clusters.
 * Handles creation, deletion, scaling, and status monitoring.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClusterService {

    private final ClusterRepository clusterRepository;
    private final NodeRepository nodeRepository;
    private final DockerService dockerService;
    private final ProxySQLService proxySQLService;
    private final OrchestratorService orchestratorService;
    private final NetworkService networkService;

    /**
     * Create a new MySQL HA cluster.
     * Returns immediately with PROVISIONING status, then provisions async.
     */
    @Transactional
    public Cluster createCluster(CreateClusterRequest request, String userId) {
        log.info("Creating cluster '{}' for user '{}'", request.getName(), userId);

        String clusterId = UUID.randomUUID().toString().substring(0, 8);

        // Create cluster entity with PROVISIONING status
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
                .createdAt(Instant.now())
                .build();

        // Save immediately so user can see it on dashboard
        cluster = clusterRepository.save(cluster);
        log.info("Cluster '{}' saved with PROVISIONING status, starting async provisioning", clusterId);

        // Start async provisioning in background thread
        // Note: Using CompletableFuture instead of @Async to avoid self-invocation
        // issue
        final String finalClusterId = clusterId;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            provisionClusterAsync(finalClusterId);
        });

        return cluster;
    }

    /**
     * Async provisioning of cluster infrastructure.
     */
    @Async
    public void provisionClusterAsync(String clusterId) {
        log.info("Starting async provisioning for cluster '{}'", clusterId);

        try {
            // Small delay to ensure transaction is committed
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Cluster cluster = clusterRepository.findById(clusterId).orElse(null);
        if (cluster == null) {
            log.error("Cluster '{}' not found for provisioning", clusterId);
            return;
        }

        try {
            // Phase 1: Create all containers
            createContainers(cluster);

            // Phase 2: Wait for containers to initialize
            waitForContainersToInitialize();

            // Phase 3: Configure all services
            configureServices(cluster);

            // Phase 4: Finalize
            finalizeProvisioning(cluster);

            log.info("Cluster '{}' provisioned successfully", clusterId);

        } catch (Exception e) {
            handleProvisioningFailure(cluster, e);
        }
    }

    // ========================================================================
    // PROVISIONING PHASE METHODS
    // ========================================================================

    /**
     * Phase 1: Create all Docker containers for the cluster.
     */
    private void createContainers(Cluster cluster) {
        String clusterId = cluster.getId();

        // Create isolated network
        String networkId = networkService.createClusterNetwork(clusterId);
        cluster.setNetworkId(networkId);

        // Create MySQL Master
        String masterContainerId = dockerService.createMySQLContainer(
                clusterId, "master", cluster.getMysqlVersion(), networkId);
        cluster.setMasterContainerId(masterContainerId);
        networkService.connectToSharedNetwork(masterContainerId);
        saveNode(clusterId, "mysql-" + clusterId + "-master", masterContainerId, NodeRole.MASTER, 3306, 2, "4G");

        // Create MySQL Replicas
        for (int i = 1; i <= cluster.getReplicaCount(); i++) {
            String replicaContainerId = dockerService.createMySQLContainer(
                    clusterId, "replica-" + i, cluster.getMysqlVersion(), networkId);
            cluster.getReplicaContainerIds().add(replicaContainerId);
            networkService.connectToSharedNetwork(replicaContainerId);
            saveNode(clusterId, "mysql-" + clusterId + "-replica-" + i, replicaContainerId, NodeRole.REPLICA, 3306, 2,
                    "4G");
        }

        // Create ProxySQL
        String proxySqlContainerId = dockerService.createProxySQLContainer(clusterId, networkId);
        cluster.setProxySqlContainerId(proxySqlContainerId);
        saveNode(clusterId, "proxysql-" + clusterId, proxySqlContainerId, NodeRole.PROXY, 6033, 1, "1G");

        // Persist cluster state
        clusterRepository.save(cluster);
        log.info("All containers created for cluster '{}'", clusterId);
    }

    /**
     * Phase 2: Wait for containers to fully initialize.
     */
    private void waitForContainersToInitialize() throws InterruptedException {
        log.info("Waiting 2 minutes for containers to fully initialize...");
        Thread.sleep(2 * 60 * 1000);
    }

    /**
     * Phase 3: Configure all services (MySQL, ProxySQL, Orchestrator).
     */
    private void configureServices(Cluster cluster) {
        // Setup MySQL Primary (replication user, orchestrator user)
        safeExecute("Setup Primary", () -> dockerService.setupPrimary(cluster));

        // Setup replication on replicas
        safeExecute("Setup Replication", () -> dockerService.setupReplication(cluster));

        // Configure ProxySQL
        safeExecute("Configure ProxySQL", () -> proxySQLService.configureCluster(cluster));

        // Register with Orchestrator
        safeExecute("Register Orchestrator", () -> orchestratorService.registerCluster(cluster));
    }

    /**
     * Phase 4: Mark cluster as running.
     */
    private void finalizeProvisioning(Cluster cluster) {
        cluster.setStatus(ClusterStatus.RUNNING);
        cluster.setUpdatedAt(Instant.now());
        clusterRepository.save(cluster);
    }

    /**
     * Handle provisioning failure with cleanup.
     */
    private void handleProvisioningFailure(Cluster cluster, Exception e) {
        log.error("Failed to provision cluster '{}': {}", cluster.getId(), e.getMessage(), e);
        cluster.setStatus(ClusterStatus.FAILED);
        cluster.setErrorMessage(e.getMessage());
        cluster.setUpdatedAt(Instant.now());
        clusterRepository.save(cluster);
        cleanupCluster(cluster);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Save a node entity to the database.
     */
    private void saveNode(String clusterId, String containerName, String containerId, NodeRole role, int port,
            int cpuCores, String memory) {
        Node node = Node.builder()
                .clusterId(clusterId)
                .containerName(containerName)
                .containerId(containerId)
                .role(role)
                .port(port)
                .cpuCores(cpuCores)
                .memory(memory)
                .status(NodeStatus.RUNNING)
                .readOnly(role == NodeRole.REPLICA) // Replicas are read-only
                .createdAt(Instant.now())
                .build();
        nodeRepository.save(node);
        log.info("Node '{}' ({}) created with containerId: {}", containerName, role, containerId);
    }

    /**
     * Execute an action safely, logging warnings on failure.
     */
    private void safeExecute(String actionName, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("Failed to {} (non-fatal): {}", actionName, e.getMessage());
        }
    }

    // ========================================================================
    // CLUSTER CRUD OPERATIONS
    // ========================================================================

    /**
     * Get cluster by ID.
     */
    public Cluster getCluster(String clusterId) {
        return clusterRepository.findById(clusterId)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterId));
    }

    /**
     * List all clusters for a user.
     */
    public List<Cluster> listClusters(String userId) {
        return clusterRepository.findByUserId(userId);
    }

    /**
     * Delete a cluster and all its resources.
     */
    @Transactional
    public void deleteCluster(String clusterId) {
        log.info("Deleting cluster '{}'", clusterId);

        Cluster cluster = getCluster(clusterId);
        cluster.setStatus(ClusterStatus.DELETING);
        clusterRepository.save(cluster);

        try {
            // Unregister from Orchestrator
            orchestratorService.unregisterCluster(cluster);

            // Stop and remove containers
            cleanupCluster(cluster);

            // Delete all nodes belonging to this cluster
            List<Node> nodes = nodeRepository.findByClusterId(clusterId);
            nodeRepository.deleteAll(nodes);
            log.info("Deleted {} nodes for cluster '{}'", nodes.size(), clusterId);

            // Delete cluster record
            clusterRepository.delete(cluster);

            log.info("Cluster '{}' deleted successfully", clusterId);

        } catch (Exception e) {
            log.error("Failed to delete cluster '{}'", clusterId, e);
            cluster.setStatus(ClusterStatus.FAILED);
            cluster.setErrorMessage("Delete failed: " + e.getMessage());
            clusterRepository.save(cluster);
        }
    }

    /**
     * Scale cluster by adding or removing replicas.
     */
    @Transactional
    public Cluster scaleCluster(String clusterId, ScaleRequest request) {
        log.info("Scaling cluster '{}' to {} replicas", clusterId, request.getReplicaCount());

        Cluster cluster = getCluster(clusterId);
        int currentReplicas = cluster.getReplicaContainerIds().size();
        int targetReplicas = request.getReplicaCount();

        if (targetReplicas > currentReplicas) {
            // Get resource config from request or use defaults
            int cpuCores = 2;
            String memory = "4G";
            if (request.getResources() != null) {
                if (request.getResources().getCpuCores() != null) {
                    cpuCores = request.getResources().getCpuCores();
                }
                if (request.getResources().getMemory() != null) {
                    memory = request.getResources().getMemory();
                }
            }

            // Scale up
            for (int i = currentReplicas + 1; i <= targetReplicas; i++) {
                String replicaContainerId = dockerService.createMySQLContainer(
                        clusterId, "replica-" + i, cluster.getMysqlVersion(), cluster.getNetworkId());
                cluster.getReplicaContainerIds().add(replicaContainerId);

                // Save node to database
                saveNode(clusterId, "mysql-" + clusterId + "-replica-" + i, replicaContainerId, NodeRole.REPLICA, 3306,
                        cpuCores, memory);

                // Configure replication for new replica
                dockerService.setupReplicaReplication(cluster, replicaContainerId);

                // Add to ProxySQL
                proxySQLService.addReplica(cluster, replicaContainerId);
            }
        } else if (targetReplicas < currentReplicas) {
            // Scale down
            for (int i = currentReplicas; i > targetReplicas; i--) {
                String replicaContainerId = cluster.getReplicaContainerIds().remove(i - 1);

                // Remove node from database
                nodeRepository.findByContainerId(replicaContainerId)
                        .ifPresent(nodeRepository::delete);

                // Remove from ProxySQL
                proxySQLService.removeServer(replicaContainerId);

                // Stop and remove container
                dockerService.removeContainer(replicaContainerId);
            }
        }

        cluster.setReplicaCount(targetReplicas);
        return clusterRepository.save(cluster);
    }

    /**
     * Get cluster health status.
     */
    public ClusterStatus getClusterHealth(String clusterId) {
        Cluster cluster = getCluster(clusterId);

        // Check all containers are running
        boolean masterHealthy = dockerService.isContainerHealthy(cluster.getMasterContainerId());
        boolean proxySqlHealthy = dockerService.isContainerHealthy(cluster.getProxySqlContainerId());

        long healthyReplicas = cluster.getReplicaContainerIds().stream()
                .filter(dockerService::isContainerHealthy)
                .count();

        if (!masterHealthy) {
            return ClusterStatus.DEGRADED;
        }

        if (healthyReplicas < cluster.getReplicaCount()) {
            return ClusterStatus.DEGRADED;
        }

        return ClusterStatus.RUNNING;
    }

    /**
     * Start a stopped cluster.
     */
    @Transactional
    public Cluster startCluster(String clusterId) {
        log.info("Starting cluster '{}'", clusterId);
        Cluster cluster = getCluster(clusterId);

        if (cluster.getStatus() != ClusterStatus.STOPPED) {
            throw new RuntimeException("Cluster is not in STOPPED state");
        }

        try {
            // Start containers in order
            dockerService.startContainer(cluster.getMasterContainerId());

            for (String replicaId : cluster.getReplicaContainerIds()) {
                dockerService.startContainer(replicaId);
            }

            dockerService.startContainer(cluster.getProxySqlContainerId());

            cluster.setStatus(ClusterStatus.RUNNING);
            log.info("Cluster '{}' started successfully", clusterId);

        } catch (Exception e) {
            log.error("Failed to start cluster '{}'", clusterId, e);
            cluster.setStatus(ClusterStatus.FAILED);
            cluster.setErrorMessage("Start failed: " + e.getMessage());
        }

        return clusterRepository.save(cluster);
    }

    /**
     * Stop a running cluster.
     */
    @Transactional
    public Cluster stopCluster(String clusterId) {
        log.info("Stopping cluster '{}'", clusterId);
        Cluster cluster = getCluster(clusterId);

        if (cluster.getStatus() != ClusterStatus.RUNNING) {
            throw new RuntimeException("Cluster is not in RUNNING state");
        }

        try {
            // Stop containers in reverse order
            dockerService.stopContainer(cluster.getProxySqlContainerId());

            for (String replicaId : cluster.getReplicaContainerIds()) {
                dockerService.stopContainer(replicaId);
            }

            dockerService.stopContainer(cluster.getMasterContainerId());

            cluster.setStatus(ClusterStatus.STOPPED);
            log.info("Cluster '{}' stopped successfully", clusterId);

        } catch (Exception e) {
            log.error("Failed to stop cluster '{}'", clusterId, e);
            cluster.setErrorMessage("Stop failed: " + e.getMessage());
        }

        return clusterRepository.save(cluster);
    }

    /**
     * Cleanup cluster resources.
     */
    private void cleanupCluster(Cluster cluster) {
        try {
            // Unregister from Orchestrator first
            safeExecute("Unregister Orchestrator", () -> orchestratorService.unregisterCluster(cluster));

            // Remove ProxySQL
            if (cluster.getProxySqlContainerId() != null) {
                dockerService.removeContainer(cluster.getProxySqlContainerId());
            }

            // Remove replicas
            for (String replicaId : cluster.getReplicaContainerIds()) {
                dockerService.removeContainer(replicaId);
            }

            // Remove master
            if (cluster.getMasterContainerId() != null) {
                dockerService.removeContainer(cluster.getMasterContainerId());
            }

            // Remove network
            if (cluster.getNetworkId() != null) {
                networkService.removeNetwork(cluster.getNetworkId());
            }

        } catch (Exception e) {
            log.warn("Error during cluster cleanup: {}", e.getMessage());
        }
    }
}
