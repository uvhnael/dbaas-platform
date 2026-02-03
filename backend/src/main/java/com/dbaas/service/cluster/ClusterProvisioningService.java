package com.dbaas.service.cluster;

import com.dbaas.exception.DockerOperationException;
import com.dbaas.model.Cluster;
import com.dbaas.model.NodeRole;
import com.dbaas.model.dto.CreateClusterRequest;
import com.dbaas.repository.ClusterRepository;
import com.dbaas.service.DockerService;
import com.dbaas.service.NetworkService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Service responsible for cluster provisioning - creating Docker containers,
 * networks, and initializing MySQL instances.
 * 
 * <p>
 * Extracted from ClusterService to follow Single Responsibility Principle.
 * </p>
 */
@Service
@Slf4j
public class ClusterProvisioningService {

    private final ClusterRepository clusterRepository;
    private final DockerService dockerService;
    private final NetworkService networkService;
    private final CircuitBreaker dockerCircuitBreaker;
    private final Retry dockerRetry;
    private final NodePersistenceService nodePersistenceService;

    // Caffeine cache with TTL for temporary provisioning configs (auto-eviction
    // prevents memory leaks)
    private final Cache<String, ProvisioningConfig> provisioningConfigs = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();

    public ClusterProvisioningService(
            ClusterRepository clusterRepository,
            DockerService dockerService,
            NetworkService networkService,
            CircuitBreaker dockerCircuitBreaker,
            Retry dockerRetry,
            NodePersistenceService nodePersistenceService) {
        this.clusterRepository = clusterRepository;
        this.dockerService = dockerService;
        this.networkService = networkService;
        this.dockerCircuitBreaker = dockerCircuitBreaker;
        this.dockerRetry = dockerRetry;
        this.nodePersistenceService = nodePersistenceService;
    }

    /**
     * Temporary config holder for async provisioning.
     */
    public record ProvisioningConfig(
            CreateClusterRequest.NodeConfig masterConfig,
            List<CreateClusterRequest.NodeConfig> replicaConfigs) {
    }

    /**
     * Store provisioning configuration for async access.
     */
    public void storeProvisioningConfig(String clusterId, CreateClusterRequest request) {
        CreateClusterRequest.NodeConfig masterConfig = request.getMasterNodeConfig();
        List<CreateClusterRequest.NodeConfig> replicaConfigs = new ArrayList<>();
        for (int i = 1; i <= request.getReplicaCount(); i++) {
            replicaConfigs.add(request.getReplicaNodeConfig(i));
        }
        provisioningConfigs.put(clusterId, new ProvisioningConfig(masterConfig, replicaConfigs));
    }

    /**
     * Clear provisioning configuration after use.
     */
    public void clearProvisioningConfig(String clusterId) {
        provisioningConfigs.invalidate(clusterId);
    }

    /**
     * Create all Docker containers for a cluster with retry logic.
     * Saves nodes immediately in separate transactions.
     */
    public void createContainersWithRetry(Cluster cluster) {
        String clusterId = cluster.getId();

        try {
            // Get provisioning config from temporary Caffeine cache
            ProvisioningConfig config = provisioningConfigs.getIfPresent(clusterId);
            if (config == null) {
                log.warn("No provisioning config found for cluster '{}', using defaults", clusterId);
                config = new ProvisioningConfig(
                        CreateClusterRequest.NodeConfig.builder().build(),
                        new ArrayList<>());
            }

            // Wrap container creation with retry
            Supplier<String> createNetwork = Retry.decorateSupplier(dockerRetry,
                    () -> networkService.createClusterNetwork(clusterId));

            String networkId = CircuitBreaker.decorateSupplier(dockerCircuitBreaker, createNetwork).get();
            cluster.setNetworkId(networkId);

            // Create MySQL Master with retry and custom resources
            createMasterContainer(cluster, config);

            // Create MySQL Replicas with individual configurations
            createReplicaContainers(cluster, config);

            // Create ProxySQL
            createProxySQLContainer(cluster);

            clusterRepository.save(cluster);
            log.info("All containers created for cluster '{}'", clusterId);

            // Clean up provisioning config after successful container creation
            provisioningConfigs.invalidate(clusterId);

        } catch (CallNotPermittedException e) {
            // Circuit breaker is open - Docker service is unavailable
            log.error("Circuit breaker open for cluster '{}': Docker service unavailable", clusterId);
            provisioningConfigs.invalidate(clusterId);
            throw new DockerOperationException(
                    "Docker service is temporarily unavailable. Please try again in a few minutes.", e);
        } catch (Exception e) {
            log.error("Failed to create containers for cluster '{}': {}", clusterId, e.getMessage(), e);
            provisioningConfigs.invalidate(clusterId);
            throw e;
        }
    }

    private void createMasterContainer(Cluster cluster, ProvisioningConfig config) {
        String clusterId = cluster.getId();
        CreateClusterRequest.NodeConfig masterNodeConfig = config.masterConfig();
        final int masterCpu = masterNodeConfig.getCpuCores();
        final String masterMemory = masterNodeConfig.getMemory();
        final String masterStorage = masterNodeConfig.getStorage();

        log.info("Creating MySQL Master for cluster '{}' with {} vCPU, {} RAM, {} storage",
                clusterId, masterCpu, masterMemory, masterStorage);

        Supplier<String> createMaster = Retry.decorateSupplier(dockerRetry,
                () -> dockerService.createMySQLContainer(
                        clusterId, "master", cluster.getMysqlVersion(), cluster.getNetworkId(),
                        masterCpu, masterMemory, masterStorage));

        String masterContainerId = CircuitBreaker.decorateSupplier(dockerCircuitBreaker, createMaster).get();
        cluster.setMasterContainerId(masterContainerId);

        networkService.connectToSharedNetwork(masterContainerId);
        nodePersistenceService.saveNodeImmediately(clusterId, "mysql-" + clusterId + "-master", masterContainerId,
                NodeRole.MASTER, 3306,
                masterCpu, masterMemory, masterStorage);
        log.info("MySQL Master created successfully: {}", masterContainerId);
    }

    private void createReplicaContainers(Cluster cluster, ProvisioningConfig config) {
        String clusterId = cluster.getId();

        for (int i = 1; i <= cluster.getReplicaCount(); i++) {
            final int replicaNum = i;

            CreateClusterRequest.NodeConfig replicaNodeConfig = getReplicaConfig(config, i);
            final int replicaCpu = replicaNodeConfig.getCpuCores();
            final String replicaMemory = replicaNodeConfig.getMemory();
            final String replicaStorage = replicaNodeConfig.getStorage();

            log.info("Creating MySQL Replica {} for cluster '{}' with {} vCPU, {} RAM, {} storage",
                    replicaNum, clusterId, replicaCpu, replicaMemory, replicaStorage);

            Supplier<String> createReplica = Retry.decorateSupplier(dockerRetry,
                    () -> dockerService.createMySQLContainer(
                            clusterId, "replica-" + replicaNum, cluster.getMysqlVersion(), cluster.getNetworkId(),
                            replicaCpu, replicaMemory, replicaStorage));

            String replicaContainerId = CircuitBreaker.decorateSupplier(dockerCircuitBreaker, createReplica).get();
            cluster.getReplicaContainerIds().add(replicaContainerId);

            networkService.connectToSharedNetwork(replicaContainerId);
            nodePersistenceService.saveNodeImmediately(clusterId, "mysql-" + clusterId + "-replica-" + i,
                    replicaContainerId,
                    NodeRole.REPLICA, 3306, replicaCpu, replicaMemory, replicaStorage);
            log.info("MySQL Replica {} created successfully: {}", replicaNum, replicaContainerId);
        }
    }

    private void createProxySQLContainer(Cluster cluster) {
        String clusterId = cluster.getId();
        log.info("Creating ProxySQL for cluster '{}'", clusterId);

        Supplier<String> createProxy = Retry.decorateSupplier(dockerRetry,
                () -> dockerService.createProxySQLContainer(clusterId, cluster.getNetworkId()));

        String proxySqlContainerId = CircuitBreaker.decorateSupplier(dockerCircuitBreaker, createProxy).get();
        cluster.setProxySqlContainerId(proxySqlContainerId);

        nodePersistenceService.saveNodeImmediately(clusterId, "proxysql-" + clusterId, proxySqlContainerId,
                NodeRole.PROXY, 6033,
                1, "1G", "1G");
        log.info("ProxySQL created successfully: {}", proxySqlContainerId);
    }

    /**
     * Create a single replica container for scaling up.
     */
    public String createReplicaContainer(Cluster cluster, int replicaNum, int cpuCores, String memory, String storage) {
        String clusterId = cluster.getId();

        log.info("Creating MySQL Replica {} for cluster '{}' with {} vCPU, {} RAM, {} storage",
                replicaNum, clusterId, cpuCores, memory, storage);

        Supplier<String> createReplica = Retry.decorateSupplier(dockerRetry,
                () -> dockerService.createMySQLContainer(
                        clusterId, "replica-" + replicaNum, cluster.getMysqlVersion(), cluster.getNetworkId(),
                        cpuCores, memory, storage));

        String replicaContainerId = CircuitBreaker.decorateSupplier(dockerCircuitBreaker, createReplica).get();

        networkService.connectToSharedNetwork(replicaContainerId);
        nodePersistenceService.saveNodeImmediately(clusterId, "mysql-" + clusterId + "-replica-" + replicaNum,
                replicaContainerId, NodeRole.REPLICA, 3306, cpuCores, memory, storage);

        log.info("MySQL Replica {} created: {}", replicaNum, replicaContainerId);
        return replicaContainerId;
    }

    /**
     * Get replica config from provisioning config (1-indexed).
     */
    private CreateClusterRequest.NodeConfig getReplicaConfig(ProvisioningConfig config, int replicaNumber) {
        int index = replicaNumber - 1;
        if (config.replicaConfigs() != null && index >= 0 && index < config.replicaConfigs().size()) {
            return config.replicaConfigs().get(index);
        }
        return CreateClusterRequest.NodeConfig.builder().build();
    }

    /**
     * Delegate to NodePersistenceService for proper transaction boundary.
     * Kept for backward compatibility with ClusterScalingService.
     */
    public void updateNodeStatusToRunning(String containerId) {
        nodePersistenceService.updateNodeStatusToRunning(containerId);
    }

    /**
     * Cleanup cluster resources (containers and network).
     */
    public void cleanupCluster(Cluster cluster) {
        try {
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
