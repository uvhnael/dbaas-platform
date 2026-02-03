package com.dbaas.service.cluster;

import com.dbaas.model.Node;
import com.dbaas.model.NodeRole;
import com.dbaas.model.NodeStatus;
import com.dbaas.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service for Node persistence operations with proper transaction boundaries.
 * 
 * <p>
 * Extracted from ClusterProvisioningService to fix Spring AOP proxy bypass
 * issue.
 * When methods with @Transactional(REQUIRES_NEW) are called internally within
 * the same bean,
 * Spring AOP proxy is bypassed and the transaction propagation doesn't work.
 * </p>
 * 
 * <p>
 * By extracting these methods to a separate @Service bean, Spring can properly
 * intercept the calls and apply the REQUIRES_NEW propagation.
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NodePersistenceService {

    private final NodeRepository nodeRepository;

    /**
     * Save node immediately in a new transaction.
     * Uses REQUIRES_NEW to ensure node is persisted even if outer transaction rolls
     * back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveNodeImmediately(String clusterId, String containerName, String containerId, NodeRole role,
            int port, int cpuCores, String memory, String storage) {
        Node node = Node.builder()
                .clusterId(clusterId)
                .containerName(containerName)
                .containerId(containerId)
                .role(role)
                .port(port)
                .cpuCores(cpuCores)
                .memory(memory)
                .storage(storage)
                .status(NodeStatus.STARTING)
                .readOnly(role == NodeRole.REPLICA)
                .createdAt(Instant.now())
                .build();
        nodeRepository.save(node);
        nodeRepository.flush();
        log.info("Node '{}' ({}) saved with STARTING status, {} vCPU, {} RAM, {} storage",
                containerName, role, cpuCores, memory, storage);
    }

    /**
     * Update node status to RUNNING in a new transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateNodeStatusToRunning(String containerId) {
        nodeRepository.findByContainerId(containerId).ifPresent(node -> {
            node.setStatus(NodeStatus.RUNNING);
            nodeRepository.save(node);
            nodeRepository.flush();
            log.info("Node '{}' status updated to RUNNING", node.getContainerName());
        });
    }

    /**
     * Update node status to a specific status in a new transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateNodeStatus(String containerId, NodeStatus status) {
        nodeRepository.findByContainerId(containerId).ifPresent(node -> {
            node.setStatus(status);
            nodeRepository.save(node);
            nodeRepository.flush();
            log.info("Node '{}' status updated to {}", node.getContainerName(), status);
        });
    }

    /**
     * Delete node by container ID in a new transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteNodeByContainerId(String containerId) {
        nodeRepository.findByContainerId(containerId).ifPresent(node -> {
            nodeRepository.delete(node);
            nodeRepository.flush();
            log.info("Node '{}' deleted", node.getContainerName());
        });
    }
}
