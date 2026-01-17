package com.dbaas.repository;

import com.dbaas.model.Task;
import com.dbaas.model.TaskStatus;
import com.dbaas.model.TaskType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Task entities.
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, String> {

    /**
     * Find all tasks for a cluster.
     */
    List<Task> findByClusterIdOrderByCreatedAtDesc(String clusterId);

    /**
     * Find tasks by status.
     */
    List<Task> findByStatus(TaskStatus status);

    /**
     * Find running tasks for a cluster.
     */
    List<Task> findByClusterIdAndStatus(String clusterId, TaskStatus status);

    /**
     * Find tasks by type and status.
     */
    List<Task> findByTypeAndStatus(TaskType type, TaskStatus status);

    /**
     * Count pending tasks.
     */
    long countByStatus(TaskStatus status);
}
