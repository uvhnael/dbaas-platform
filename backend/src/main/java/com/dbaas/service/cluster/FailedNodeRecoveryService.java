package com.dbaas.service.cluster;

import com.dbaas.model.Cluster;
import com.dbaas.model.Node;
import com.dbaas.model.NodeRole;
import com.dbaas.model.NodeStatus;
import com.dbaas.repository.ClusterRepository;
import com.dbaas.repository.NodeRepository;
import com.dbaas.service.DockerService;
import com.dbaas.service.NotificationService;
import com.dbaas.service.OrchestratorService;
import com.dbaas.service.ProxySQLService;
import com.dbaas.service.SecretService;
import com.dbaas.util.HostnameUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service responsible for recovering failed nodes after failover.
 * 
 * <p>
 * When a master fails and Orchestrator promotes a replica, this service:
 * 1. Removes the failed master container
 * 2. Creates a new MySQL replica container
 * 3. Clones data from the new master
 * 4. Configures replication to the new master
 * 5. Adds the recovered node to ProxySQL
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FailedNodeRecoveryService {

    private final DockerService dockerService;
    private final ClusterProvisioningService provisioningService;
    private final NodePersistenceService nodePersistenceService;
    private final ProxySQLService proxySQLService;
    private final NotificationService notificationService;
    private final SecretService secretService;
    private final OrchestratorService orchestratorService;
    private final ClusterRepository clusterRepository;
    private final NodeRepository nodeRepository;

    private static final int DEFAULT_CPU_CORES = 2;
    private static final String DEFAULT_MEMORY = "4G";
    private static final String DEFAULT_STORAGE = "10G";

    /**
     * Recover failed master as a new replica asynchronously.
     * This method is called after failover handling completes.
     * 
     * @param clusterId     The cluster ID that experienced failover
     * @param failedHost    The hostname of the failed master (e.g.,
     *                      mysql-abc123-master)
     * @param newMasterHost The hostname of the promoted replica (new master)
     */
    @Async
    public void recoverFailedMasterAsReplica(String clusterId, String failedHost, String newMasterHost) {
        log.info("[Recovery:{}] Starting auto-recovery: {} -> new replica", clusterId, failedHost);

        Cluster cluster = null;

        try {
            // IMPORTANT: Reload cluster from DB to get fresh version (avoid optimistic
            // locking)
            cluster = clusterRepository.findById(clusterId)
                    .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterId));

            // Step 1: Find and remove the failed node
            String cleanFailedHost = HostnameUtils.removePort(failedHost);
            if (cleanFailedHost == null) {
                log.warn("[Recovery:{}] No failed host specified, skipping recovery", clusterId);
                return;
            }

            Optional<Node> failedNodeOpt = nodeRepository.findByContainerName(cleanFailedHost);
            if (failedNodeOpt.isEmpty()) {
                log.warn("[Recovery:{}] Failed node '{}' not found in database", clusterId, cleanFailedHost);
                return;
            }

            Node failedNode = failedNodeOpt.get();
            String failedContainerId = failedNode.getContainerId();

            // Step 2: Unregister from Orchestrator and remove the failed container
            try {
                orchestratorService.unregisterNode(cleanFailedHost);
            } catch (Exception e) {
                log.warn("[Recovery:{}] Orchestrator unregistration failed: {}", clusterId, e.getMessage());
            }

            try {
                dockerService.removeContainer(failedContainerId);
            } catch (Exception e) {
                log.warn("[Recovery:{}] Container removal failed: {}", clusterId, e.getMessage());
            }

            // Step 2.5: Update DB immediately after container removal
            // Remove from cluster's replica list (or master list if it was master)
            cluster.getReplicaContainerIds().remove(failedContainerId);
            if (failedContainerId.equals(cluster.getMasterContainerId())) {
                cluster.setMasterContainerId(null);
            }
            clusterRepository.save(cluster);

            // Delete the failed node record
            nodeRepository.delete(failedNode);

            // Step 3: Determine the new replica number
            int newReplicaNum = determineNextReplicaNumber(cluster);
            String newContainerName = "mysql-" + clusterId + "-replica-" + newReplicaNum;

            // Step 4: Get resource config from failed node or use defaults
            int cpuCores = failedNode.getCpuCores() > 0 ? failedNode.getCpuCores() : DEFAULT_CPU_CORES;
            String memory = failedNode.getMemory() != null && !failedNode.getMemory().isEmpty()
                    ? failedNode.getMemory()
                    : DEFAULT_MEMORY;

            // Step 5: Create new replica container
            String newContainerId = provisioningService.createReplicaContainer(
                    cluster, newReplicaNum, cpuCores, memory, DEFAULT_STORAGE);

            // Step 6: Wait for container to be healthy
            waitForContainerHealthy(newContainerId, 60);

            // Step 7: Clone data from new master
            cloneDataFromMaster(clusterId, newMasterHost, newContainerId);

            // Step 8: Setup replication to new master
            setupReplicationToNewMaster(cluster, newContainerId, newMasterHost);

            // Step 9: Add new replica to cluster and update status
            // IMPORTANT: Reload cluster from DB to get fresh version (avoid optimistic
            // locking)
            Cluster freshCluster = clusterRepository.findById(clusterId)
                    .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterId));
            freshCluster.getReplicaContainerIds().add(newContainerId);
            clusterRepository.save(freshCluster);
            nodePersistenceService.updateNodeStatusToRunning(newContainerId);

            // Step 11: Add recovered replica to ProxySQL
            proxySQLService.addReplica(freshCluster, newContainerId);

            // Step 12: Send notification
            notificationService.broadcast("RECOVERY_COMPLETE",
                    String.format("Cluster %s: Failed master recovered as replica %s",
                            freshCluster.getName(), newContainerName));

            log.info("[Recovery:{}] Completed: new replica {}", clusterId, newContainerName);

        } catch (Exception e) {
            log.error("[Recovery:{}] Failed: {}", clusterId, e.getMessage(), e);

            String clusterName = (cluster != null) ? cluster.getName() : "Unknown (" + clusterId + ")";
            notificationService.broadcast("RECOVERY_FAILED",
                    String.format("Cluster %s: Auto-recovery failed - %s",
                            clusterName, e.getMessage()));
        }
    }

    /**
     * Clone data from master to the new replica using mysqldump.
     */
    private void cloneDataFromMaster(String clusterId, String masterHost, String targetContainerId) {
        String cleanMasterHost = HostnameUtils.removePort(masterHost);
        String rootPassword = secretService.generateMySQLRootPassword(clusterId);

        // Use docker exec to pipe mysqldump directly
        // mysqldump from master | mysql on replica
        String dumpCommand = String.format(
                "mysqldump -h%s -uroot -p'%s' --all-databases --single-transaction " +
                        "--gtid-mode=ON --set-gtid-purged=ON --triggers --routines --events " +
                        "| mysql -uroot -p'%s'",
                cleanMasterHost, rootPassword, rootPassword);

        dockerService.execInContainer(targetContainerId, "sh", "-c", dumpCommand);
    }

    /**
     * Setup replication from replica to the new master.
     */
    private void setupReplicationToNewMaster(Cluster cluster, String replicaContainerId, String masterHost) {
        String clusterId = cluster.getId();
        String cleanMasterHost = HostnameUtils.removePort(masterHost);
        String rootPassword = secretService.generateMySQLRootPassword(clusterId);
        String replicationPassword = secretService.generateReplicationPassword(clusterId);

        String replicationSql = String.format(
                "STOP REPLICA; " +
                        "RESET REPLICA ALL; " +
                        "CHANGE REPLICATION SOURCE TO " +
                        "SOURCE_HOST='%s', " +
                        "SOURCE_USER='repl', " +
                        "SOURCE_PASSWORD='%s', " +
                        "SOURCE_AUTO_POSITION=1; " +
                        "START REPLICA;",
                cleanMasterHost, replicationPassword);

        dockerService.execInContainer(replicaContainerId, "mysql", "-uroot", "-p" + rootPassword, "-e", replicationSql);
    }

    /**
     * Determine the next replica number for the cluster.
     * 
     * IMPORTANT: This checks ALL nodes in the cluster (including promoted master)
     * to avoid naming conflicts when a replica was promoted to master.
     */
    private int determineNextReplicaNumber(Cluster cluster) {
        // Find the highest existing replica number from ALL nodes in the cluster
        // This includes:
        // 1. Current replicas
        // 2. Current master (which may have been a promoted replica like
        // "mysql-xxx-replica-2")
        int maxNum = 0;

        // Check all nodes in the cluster (master + replicas)
        List<Node> allNodes = nodeRepository.findByClusterId(cluster.getId());
        for (Node node : allNodes) {
            try {
                String name = node.getContainerName();
                if (name != null && name.contains("-replica-")) {
                    int num = Integer.parseInt(name.substring(name.lastIndexOf("-") + 1));
                    maxNum = Math.max(maxNum, num);
                }
            } catch (Exception ignored) {
            }
        }

        return maxNum + 1;
    }

    /**
     * Wait for container to become healthy.
     */
    private void waitForContainerHealthy(String containerId, int maxWaitSeconds) {
        int waited = 0;
        while (waited < maxWaitSeconds) {
            if (dockerService.isContainerHealthy(containerId)) {
                return;
            }
            try {
                Thread.sleep(5000);
                waited += 5;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for container health", e);
            }
        }
        throw new RuntimeException(
                "Container " + containerId + " did not become healthy within " + maxWaitSeconds + "s");
    }
}