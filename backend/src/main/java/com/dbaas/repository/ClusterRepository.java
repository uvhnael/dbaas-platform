package com.dbaas.repository;

import com.dbaas.model.Cluster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Cluster entity.
 */
@Repository
public interface ClusterRepository extends JpaRepository<Cluster, String> {

    /**
     * Find all clusters for a user.
     */
    List<Cluster> findByUserId(String userId);

    /**
     * Count clusters for a user.
     */
    int countByUserId(String userId);

    /**
     * Find clusters by status.
     */
    List<Cluster> findByStatus(com.dbaas.model.ClusterStatus status);

    /**
     * Check if cluster name exists for user.
     */
    boolean existsByNameAndUserId(String name, String userId);
}
