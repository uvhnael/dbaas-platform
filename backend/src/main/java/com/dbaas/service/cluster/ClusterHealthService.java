package com.dbaas.service.cluster;

import com.dbaas.exception.ClusterNotFoundException;
import com.dbaas.exception.ContainerNotReadyException;
import com.dbaas.model.Cluster;
import com.dbaas.model.ClusterStatus;
import com.dbaas.model.Node;
import com.dbaas.model.NodeStatus;
import com.dbaas.repository.ClusterRepository;
import com.dbaas.repository.NodeRepository;
import com.dbaas.service.DockerService;
import com.dbaas.util.AsyncRetryUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Service responsible for cluster health monitoring and status checks.
 * 
 * <p>
 * Extracted from ClusterService to follow Single Responsibility Principle.
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClusterHealthService {

    private final ClusterRepository clusterRepository;
    private final NodeRepository nodeRepository;
    private final DockerService dockerService;
    private final AsyncRetryUtils asyncRetryUtils;

    /**
     * Get cluster health status by checking all containers.
     */
    public ClusterStatus getClusterHealth(String clusterId, String userId) {
        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new ClusterNotFoundException(clusterId));

        // Verify ownership
        if (!cluster.getUserId().equals(userId)) {
            throw new AccessDeniedException("Not authorized to access this cluster");
        }

        return checkClusterHealth(cluster);
    }

    /**
     * Get cluster health status (internal use - no ownership check).
     * Use for system-level operations like provisioning and webhooks.
     */
    public ClusterStatus getClusterHealthInternal(String clusterId) {
        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new ClusterNotFoundException(clusterId));
        return checkClusterHealth(cluster);
    }

    /**
     * Check health of a cluster by examining container states.
     */
    private ClusterStatus checkClusterHealth(Cluster cluster) {
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
     * Check if a single container is healthy.
     */
    public boolean isContainerHealthy(String containerId) {
        return dockerService.isContainerHealthy(containerId);
    }

    /**
     * Wait for all containers in a cluster to become healthy.
     * 
     * @param cluster        The cluster to monitor
     * @param maxWaitSeconds Maximum time to wait
     * @throws RuntimeException if containers don't become healthy within timeout
     */
    public void waitForContainersHealthy(Cluster cluster, int maxWaitSeconds) {
        log.info("Waiting for containers to be healthy (timeout: {}s)...", maxWaitSeconds);

        int pollIntervalSeconds = 5;
        int elapsedSeconds = 0;

        while (elapsedSeconds < maxWaitSeconds) {
            boolean masterHealthy = dockerService.isContainerHealthy(cluster.getMasterContainerId());

            long healthyReplicas = cluster.getReplicaContainerIds().stream()
                    .filter(dockerService::isContainerHealthy)
                    .count();

            boolean allReplicasHealthy = healthyReplicas == cluster.getReplicaCount();
            boolean proxyHealthy = dockerService.isContainerHealthy(cluster.getProxySqlContainerId());

            log.info("Progress: Master={}, Replicas={}/{}, Proxy={} (Elapsed: {}s)",
                    masterHealthy ? "OK" : "WAIT",
                    healthyReplicas, cluster.getReplicaCount(),
                    proxyHealthy ? "OK" : "WAIT",
                    elapsedSeconds);

            if (masterHealthy && allReplicasHealthy && proxyHealthy) {
                log.info("All containers healthy after {} seconds", elapsedSeconds);
                return;
            }

            // Non-blocking delay using AsyncRetryUtils
            asyncRetryUtils.delay(Duration.ofSeconds(pollIntervalSeconds)).join();
            elapsedSeconds += pollIntervalSeconds;
        }

        throw new ContainerNotReadyException("cluster-containers", maxWaitSeconds);
    }

    /**
     * Wait for a single container to become healthy.
     */
    public void waitForSingleContainerHealthy(String containerId, int maxWaitSeconds) {
        log.info("Waiting for container {} to be healthy...", containerId);

        int pollIntervalSeconds = 5;
        int elapsedSeconds = 0;

        while (elapsedSeconds < maxWaitSeconds) {
            if (dockerService.isContainerHealthy(containerId)) {
                log.info("Container {} is healthy after {} seconds", containerId, elapsedSeconds);
                return;
            }

            // Non-blocking delay using AsyncRetryUtils
            asyncRetryUtils.delay(Duration.ofSeconds(pollIntervalSeconds)).join();
            elapsedSeconds += pollIntervalSeconds;
        }

        throw new ContainerNotReadyException(containerId, maxWaitSeconds);
    }

    /**
     * Update all nodes status to RUNNING after cluster is healthy.
     */
    public void markAllNodesRunning(String clusterId) {
        List<Node> nodes = nodeRepository.findByClusterId(clusterId);
        for (Node node : nodes) {
            if (node.getStatus() == NodeStatus.STARTING) {
                node.setStatus(NodeStatus.RUNNING);
            }
        }
        nodeRepository.saveAll(nodes);
        nodeRepository.flush();
        log.info("Marked {} nodes as RUNNING for cluster '{}'", nodes.size(), clusterId);
    }

    /**
     * Check cluster overall status based on node health.
     */
    public ClusterHealthSummary getClusterHealthSummary(String clusterId) {
        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new ClusterNotFoundException(clusterId));

        boolean masterHealthy = dockerService.isContainerHealthy(cluster.getMasterContainerId());
        boolean proxyHealthy = dockerService.isContainerHealthy(cluster.getProxySqlContainerId());

        long healthyReplicas = cluster.getReplicaContainerIds().stream()
                .filter(dockerService::isContainerHealthy)
                .count();

        int totalNodes = 2 + cluster.getReplicaCount(); // master + proxy + replicas
        int healthyNodes = (masterHealthy ? 1 : 0) + (proxyHealthy ? 1 : 0) + (int) healthyReplicas;

        return new ClusterHealthSummary(
                cluster.getId(),
                cluster.getStatus(),
                totalNodes,
                healthyNodes,
                masterHealthy,
                proxyHealthy,
                (int) healthyReplicas,
                cluster.getReplicaCount());
    }

    /**
     * Summary of cluster health.
     */
    public record ClusterHealthSummary(
            String clusterId,
            ClusterStatus status,
            int totalNodes,
            int healthyNodes,
            boolean masterHealthy,
            boolean proxyHealthy,
            int healthyReplicas,
            int totalReplicas) {
        public boolean isFullyHealthy() {
            return totalNodes == healthyNodes;
        }

        public double healthPercentage() {
            return totalNodes > 0 ? (double) healthyNodes / totalNodes * 100 : 0;
        }
    }
}
