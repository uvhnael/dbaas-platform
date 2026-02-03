package com.dbaas.controller;

import com.dbaas.model.Backup;
import com.dbaas.model.dto.ApiResponse;
import com.dbaas.service.BackupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for backup management.
 */
@RestController
@RequestMapping("/api/v1/clusters/{clusterId}/backups")
@RequiredArgsConstructor
@Tag(name = "Backups", description = "Cluster Backup Management API")
public class BackupController {

        private final BackupService backupService;

        @GetMapping
        @Operation(summary = "List all backups for a cluster")
        public ResponseEntity<ApiResponse<List<Backup>>> listBackups(@PathVariable String clusterId) {
                List<Backup> backups = backupService.listBackups(clusterId);
                return ResponseEntity.ok(ApiResponse.success(backups));
        }

        @PostMapping
        @Operation(summary = "Create a new backup")
        public ResponseEntity<ApiResponse<Backup>> createBackup(
                        @PathVariable String clusterId,
                        @RequestBody(required = false) CreateBackupRequest request) {
                String backupName = request != null ? request.name() : null;
                Backup backup = backupService.createBackup(clusterId, backupName);
                return ResponseEntity.ok(ApiResponse.success(backup, "Backup created successfully"));
        }

        @GetMapping("/{backupId}")
        @Operation(summary = "Get backup details")
        public ResponseEntity<ApiResponse<Backup>> getBackup(
                        @PathVariable String clusterId,
                        @PathVariable String backupId) {
                Backup backup = backupService.getBackup(clusterId, backupId);
                return ResponseEntity.ok(ApiResponse.success(backup));
        }

        @DeleteMapping("/{backupId}")
        @Operation(summary = "Delete a backup")
        public ResponseEntity<ApiResponse<Void>> deleteBackup(
                        @PathVariable String clusterId,
                        @PathVariable String backupId) {
                backupService.deleteBackup(clusterId, backupId);
                return ResponseEntity.ok(ApiResponse.success("Backup deleted successfully"));
        }

        @PostMapping("/{backupId}/restore")
        @Operation(summary = "Restore from a backup")
        public ResponseEntity<ApiResponse<BackupService.RestoreResult>> restoreBackup(
                        @PathVariable String clusterId,
                        @PathVariable String backupId) {
                BackupService.RestoreResult result = backupService.restoreBackup(clusterId, backupId);
                return ResponseEntity.ok(ApiResponse.success(result, "Restore initiated"));
        }

        // DTOs
        public record CreateBackupRequest(String name) {
        }
}
