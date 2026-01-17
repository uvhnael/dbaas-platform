package com.dbaas.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Entity representing a database backup.
 */
@Entity
@Table(name = "backups")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Backup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "cluster_id", nullable = false)
    private String clusterId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BackupStatus status = BackupStatus.PENDING;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "storage_path")
    private String storagePath;

    @Column(name = "backup_type")
    @Builder.Default
    private String backupType = "FULL";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * Mark backup as in progress.
     */
    public void start() {
        this.status = BackupStatus.IN_PROGRESS;
    }

    /**
     * Mark backup as completed.
     */
    public void complete(long sizeBytes, String storagePath) {
        this.status = BackupStatus.COMPLETED;
        this.sizeBytes = sizeBytes;
        this.storagePath = storagePath;
        this.completedAt = Instant.now();
    }

    /**
     * Mark backup as failed.
     */
    public void fail(String error) {
        this.status = BackupStatus.FAILED;
        this.errorMessage = error;
        this.completedAt = Instant.now();
    }
}
