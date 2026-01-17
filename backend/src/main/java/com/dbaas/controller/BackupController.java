package com.dbaas.controller;

import com.dbaas.model.Backup;
import com.dbaas.model.BackupStatus;
import com.dbaas.model.dto.ApiResponse;
import com.dbaas.repository.BackupRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for backup management.
 * Note: Actual backup implementation would integrate with storage (S3/MinIO).
 */
@RestController
@RequestMapping("/api/v1/clusters/{clusterId}/backups")
@RequiredArgsConstructor
@Tag(name = "Backups", description = "Cluster Backup Management API")
public class BackupController {

        private final BackupRepository backupRepository;

        @GetMapping
        @Operation(summary = "List all backups for a cluster")
        public ResponseEntity<ApiResponse<List<Backup>>> listBackups(@PathVariable String clusterId) {
                List<Backup> backups = backupRepository.findByClusterIdOrderByCreatedAtDesc(clusterId);
                return ResponseEntity.ok(ApiResponse.success(backups));
        }

        @PostMapping
        @Operation(summary = "Create a new backup")
        public ResponseEntity<ApiResponse<Backup>> createBackup(
                        @PathVariable String clusterId,
                        @RequestBody(required = false) CreateBackupRequest request) {

                String backupName = request != null && request.name() != null
                                ? request.name()
                                : "backup-" + System.currentTimeMillis();

                Backup backup = Backup.builder()
                                .clusterId(clusterId)
                                .name(backupName)
                                .status(BackupStatus.IN_PROGRESS)
                                .build();

                backup = backupRepository.save(backup);

                // In production: trigger actual mysqldump or xtrabackup here
                // For demo, mark as completed immediately
                backup.complete(1024L * 1024 * 50, "/backups/" + backup.getId() + ".sql.gz"); // 50MB demo
                backup = backupRepository.save(backup);

                return ResponseEntity.ok(ApiResponse.success(backup, "Backup created successfully"));
        }

        @GetMapping("/{backupId}")
        @Operation(summary = "Get backup details")
        public ResponseEntity<ApiResponse<Backup>> getBackup(
                        @PathVariable String clusterId,
                        @PathVariable String backupId) {

                Backup backup = backupRepository.findById(backupId)
                                .filter(b -> b.getClusterId().equals(clusterId))
                                .orElseThrow(() -> new RuntimeException("Backup not found: " + backupId));

                return ResponseEntity.ok(ApiResponse.success(backup));
        }

        @DeleteMapping("/{backupId}")
        @Operation(summary = "Delete a backup")
        public ResponseEntity<ApiResponse<Void>> deleteBackup(
                        @PathVariable String clusterId,
                        @PathVariable String backupId) {

                Backup backup = backupRepository.findById(backupId)
                                .filter(b -> b.getClusterId().equals(clusterId))
                                .orElseThrow(() -> new RuntimeException("Backup not found: " + backupId));

                backupRepository.delete(backup);
                return ResponseEntity.ok(ApiResponse.success("Backup deleted successfully"));
        }

        @PostMapping("/{backupId}/restore")
        @Operation(summary = "Restore from a backup")
        public ResponseEntity<ApiResponse<RestoreResponse>> restoreBackup(
                        @PathVariable String clusterId,
                        @PathVariable String backupId) {

                Backup backup = backupRepository.findById(backupId)
                                .filter(b -> b.getClusterId().equals(clusterId))
                                .orElseThrow(() -> new RuntimeException("Backup not found: " + backupId));

                // In production: trigger actual restore process here
                RestoreResponse response = new RestoreResponse(
                                UUID.randomUUID().toString(),
                                clusterId,
                                backupId,
                                "STARTED",
                                "Restore process initiated from backup: " + backup.getName());

                return ResponseEntity.ok(ApiResponse.success(response, "Restore initiated"));
        }

        // DTOs
        public record CreateBackupRequest(String name) {
        }

        public record RestoreResponse(
                        String taskId,
                        String clusterId,
                        String backupId,
                        String status,
                        String message) {
        }
}
