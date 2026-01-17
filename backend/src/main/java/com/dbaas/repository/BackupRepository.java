package com.dbaas.repository;

import com.dbaas.model.Backup;
import com.dbaas.model.BackupStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Backup entity.
 */
@Repository
public interface BackupRepository extends JpaRepository<Backup, String> {

    /**
     * Find all backups for a cluster.
     */
    List<Backup> findByClusterId(String clusterId);

    /**
     * Find all backups for a cluster ordered by creation time descending.
     */
    List<Backup> findByClusterIdOrderByCreatedAtDesc(String clusterId);

    /**
     * Find backups by cluster and status.
     */
    List<Backup> findByClusterIdAndStatus(String clusterId, BackupStatus status);

    /**
     * Count backups for a cluster.
     */
    long countByClusterId(String clusterId);

    /**
     * Delete all backups for a cluster.
     */
    void deleteByClusterId(String clusterId);
}
