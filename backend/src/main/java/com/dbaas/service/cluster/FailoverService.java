package com.dbaas.service.cluster;

import com.dbaas.model.Cluster;
import com.dbaas.model.Node;
import com.dbaas.model.NodeRole;
import com.dbaas.model.NodeStatus;
import com.dbaas.repository.ClusterRepository;
import com.dbaas.repository.NodeRepository;
import com.dbaas.service.NotificationService;
import com.dbaas.service.ProxySQLService;
import com.dbaas.util.HostnameUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service responsible for handling failover operations.
 * 
 * <p>
 * Extracted from WebhookController to follow Single Responsibility Principle.
 * Controller should only handle HTTP concerns, not business logic.
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FailoverService {

    private final ClusterRepository clusterRepository;
    private final NodeRepository nodeRepository;
    private final ProxySQLService proxySQLService;
    private final NotificationService notificationService;
    private final FailedNodeRecoveryService failedNodeRecoveryService;

    /**
     * Handle failover event from Orchestrator.
     * 
     * @param cluster       The cluster experiencing failover
     * @param failedHost    The hostname of the failed master
     * @param successorHost The hostname of the promoted replica (new master)
     */
    @Transactional
    public void handleFailover(Cluster cluster, String failedHost, String successorHost) {
        String clusterId = cluster.getId();

        // 1. Update node statuses in database
        updateNodeStatuses(cluster, failedHost, successorHost);

        // 2. Reconfigure ProxySQL to point to new master
        proxySQLService.updateMaster(cluster, successorHost);

        // 3. Notify via WebSocket
        notificationService.notifyFailover(cluster,
                failedHost != null ? failedHost : "unknown",
                successorHost);

        // 4. Trigger async recovery of failed master as new replica
        failedNodeRecoveryService.recoverFailedMasterAsReplica(clusterId, failedHost, successorHost);

        log.info("[Failover] Cluster {} completed: {} -> {}", clusterId, failedHost, successorHost);
    }

    /**
     * Handle topology recovery event from Orchestrator.
     * 
     * @param cluster       The cluster experiencing topology change
     * @param oldMasterHost The hostname of the old master
     * @param newMasterHost The hostname of the new master
     */
    @Transactional
    public void handleTopologyRecovery(Cluster cluster, String oldMasterHost, String newMasterHost) {
        String clusterId = cluster.getId();

        // 1. Update node status in database
        updateNodeStatuses(cluster, oldMasterHost, newMasterHost);

        // 2. Reconfigure ProxySQL to point to new master
        if (newMasterHost != null) {
            proxySQLService.updateMaster(cluster, newMasterHost);
        }

        // 3. Notify via WebSocket
        notificationService.notifyFailover(cluster,
                oldMasterHost != null ? oldMasterHost : "unknown",
                newMasterHost != null ? newMasterHost : "unknown");
    }

    /**
     * Update node roles and statuses after failover.
     * Also updates the cluster's masterContainerId.
     */
    private void updateNodeStatuses(Cluster cluster, String oldMasterHost, String newMasterHost) {

        String newMasterContainerId = null;
        String oldMasterContainerId = null;

        // Update old master: role -> REPLICA (demote), status -> FAILED
        if (oldMasterHost != null) {
            String cleanHost = HostnameUtils.removePort(oldMasterHost);
            Optional<Node> oldMaster = nodeRepository.findByContainerName(cleanHost);
            oldMaster.ifPresent(node -> {
                node.setRole(NodeRole.REPLICA); // Demote to replica
                node.setStatus(NodeStatus.FAILED);
                node.setReadOnly(true);
                nodeRepository.save(node);
                notificationService.notifyNodeStatus(node);
            });
            oldMasterContainerId = oldMaster.map(Node::getContainerId).orElse(null);
        }

        // Update new master: role -> MASTER (promote), status -> RUNNING
        if (newMasterHost != null) {
            String cleanHost = HostnameUtils.removePort(newMasterHost);
            Optional<Node> newMaster = nodeRepository.findByContainerName(cleanHost);
            newMaster.ifPresent(node -> {
                node.setRole(NodeRole.MASTER); // Promote to master
                node.setStatus(NodeStatus.RUNNING);
                node.setReadOnly(false);
                nodeRepository.save(node);
                notificationService.notifyNodeStatus(node);
            });
            newMasterContainerId = newMaster.map(Node::getContainerId).orElse(null);
        }

        // Update cluster's masterContainerId
        if (newMasterContainerId != null) {
            updateClusterMaster(cluster.getId(), oldMasterContainerId, newMasterContainerId);
        }
    }

    /**
     * Update cluster master container ID.
     * Reloads cluster from DB to avoid optimistic locking.
     */
    private void updateClusterMaster(String clusterId, String oldMasterContainerId, String newMasterContainerId) {
        // IMPORTANT: Reload cluster from DB to get fresh version (avoid optimistic
        // locking)
        Cluster freshCluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new RuntimeException("Cluster not found: " + clusterId));

        String oldClusterMasterId = freshCluster.getMasterContainerId();
        freshCluster.setMasterContainerId(newMasterContainerId);

        // Update replicaContainerIds: add old master, remove new master
        if (oldMasterContainerId != null && !freshCluster.getReplicaContainerIds().contains(oldMasterContainerId)) {
            freshCluster.getReplicaContainerIds().add(oldMasterContainerId);
        }
        freshCluster.getReplicaContainerIds().remove(newMasterContainerId);

        clusterRepository.save(freshCluster);
    }

    // ========================================================================
    // HOSTNAME UTILITY METHODS - Delegated to HostnameUtils
    // ========================================================================

    /**
     * Extract cluster ID from hostname like "mysql-abc123-master" or
     * "mysql-abc123-replica-1".
     * 
     * @param hostname Container hostname
     * @return Cluster ID or null if cannot extract
     */
    public String extractClusterIdFromHostname(String hostname) {
        return HostnameUtils.extractClusterId(hostname);
    }
}
