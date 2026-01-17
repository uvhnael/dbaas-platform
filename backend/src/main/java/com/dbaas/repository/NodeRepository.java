package com.dbaas.repository;

import com.dbaas.model.Node;
import com.dbaas.model.NodeRole;
import com.dbaas.model.NodeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Node entities.
 */
@Repository
public interface NodeRepository extends JpaRepository<Node, String> {

    /**
     * Find all nodes for a cluster.
     */
    List<Node> findByClusterId(String clusterId);

    /**
     * Find nodes by cluster and role.
     */
    List<Node> findByClusterIdAndRole(String clusterId, NodeRole role);

    /**
     * Find master node for a cluster.
     */
    default Optional<Node> findMasterByClusterId(String clusterId) {
        return findByClusterIdAndRole(clusterId, NodeRole.MASTER).stream().findFirst();
    }

    /**
     * Find all replica nodes for a cluster.
     */
    default List<Node> findReplicasByClusterId(String clusterId) {
        return findByClusterIdAndRole(clusterId, NodeRole.REPLICA);
    }

    /**
     * Find node by container name.
     */
    Optional<Node> findByContainerName(String containerName);

    /**
     * Count nodes by status.
     */
    long countByClusterIdAndStatus(String clusterId, NodeStatus status);

    /**
     * Find node by Docker container ID.
     */
    Optional<Node> findByContainerId(String containerId);

    /**
     * Delete all nodes for a cluster.
     */
    void deleteByClusterId(String clusterId);
}
