package com.dbaas.repository;

import com.dbaas.model.Cluster;
import com.dbaas.model.ClusterStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
     * Find clusters for a user with pagination.
     */
    Page<Cluster> findByUserId(String userId, Pageable pageable);

    /**
     * Count clusters for a user.
     */
    int countByUserId(String userId);

    /**
     * Count clusters by status.
     */
    long countByStatus(ClusterStatus status);

    /**
     * Find clusters by status.
     */
    List<Cluster> findByStatus(ClusterStatus status);

    /**
     * Find clusters by status with pagination.
     */
    Page<Cluster> findByStatus(ClusterStatus status, Pageable pageable);

    /**
     * Check if cluster name exists for user.
     */
    boolean existsByNameAndUserId(String name, String userId);

    /**
     * Find clusters by user and status.
     */
    List<Cluster> findByUserIdAndStatus(String userId, ClusterStatus status);

    /**
     * Count running clusters for a user.
     */
    long countByUserIdAndStatus(String userId, ClusterStatus status);

    /**
     * Search clusters by name (case-insensitive).
     */
    @Query("SELECT c FROM Cluster c WHERE c.userId = :userId AND LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    Page<Cluster> searchByName(String userId, String name, Pageable pageable);
}
