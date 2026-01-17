package com.dbaas.model.dto;

import com.dbaas.model.TaskStatus;
import com.dbaas.model.TaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for task details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDto {

    private String id;
    private String clusterId;
    private String clusterName;
    private TaskType type;
    private TaskStatus status;

    /**
     * Progress percentage (0-100).
     */
    private int progress;

    /**
     * Current step description.
     */
    private String currentStep;

    private String logOutput;
    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt;

    /**
     * Duration in seconds.
     */
    private Long durationSeconds;
}
