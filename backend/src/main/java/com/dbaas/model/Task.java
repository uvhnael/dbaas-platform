package com.dbaas.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Entity for tracking async operations like deployment and scaling.
 */
@Entity
@Table(name = "tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "cluster_id", nullable = false)
    private String clusterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TaskStatus status = TaskStatus.PENDING;

    @Column(name = "log_output", columnDefinition = "TEXT")
    private String logOutput;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * Mark task as running.
     */
    public void start() {
        this.status = TaskStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    /**
     * Mark task as completed.
     */
    public void complete() {
        this.status = TaskStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    /**
     * Mark task as failed.
     */
    public void fail(String error) {
        this.status = TaskStatus.FAILED;
        this.completedAt = Instant.now();
        appendLog("ERROR: " + error);
    }

    /**
     * Append to log output.
     */
    public void appendLog(String log) {
        if (this.logOutput == null) {
            this.logOutput = log;
        } else {
            this.logOutput += "\n" + log;
        }
    }
}
