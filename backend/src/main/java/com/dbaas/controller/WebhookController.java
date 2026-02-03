package com.dbaas.controller;

import com.dbaas.model.Cluster;
import com.dbaas.model.dto.ApiResponse;
import com.dbaas.service.ClusterService;
import com.dbaas.service.NotificationService;
import com.dbaas.service.cluster.FailoverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for handling Orchestrator webhooks.
 * Handles failover events and topology changes.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Webhooks", description = "Webhook handlers for external services")
public class WebhookController {

    private final ClusterService clusterService;
    private final FailoverService failoverService;
    private final NotificationService notificationService;

    /**
     * Handle Orchestrator topology-recovery webhook.
     * Called when Orchestrator performs a failover.
     */
    @PostMapping("/orchestrator/topology-recovery")
    @Operation(summary = "Handle Orchestrator topology recovery event")
    public ResponseEntity<ApiResponse<Void>> handleTopologyRecovery(@RequestBody Map<String, Object> event) {
        log.debug("Received topology-recovery event: {}", event);

        try {
            String clusterId = extractClusterId(event);
            String oldMasterHost = (String) event.get("FailedInstanceKey");
            String newMasterHost = (String) event.get("SuccessorInstanceKey");

            if (clusterId == null) {
                log.warn("Could not extract cluster ID from event");
                return ResponseEntity.ok(ApiResponse.success("Event received but cluster ID not found"));
            }

            Cluster cluster = clusterService.getClusterInternal(clusterId);

            // Delegate to FailoverService
            failoverService.handleTopologyRecovery(cluster, oldMasterHost, newMasterHost);

            return ResponseEntity.ok(ApiResponse.success("Topology recovery processed successfully"));

        } catch (Exception e) {
            log.error("Failed to handle topology-recovery event", e);
            return ResponseEntity.ok(ApiResponse.error("PROCESSING_ERROR", e.getMessage()));
        }
    }

    @PostMapping("/orchestrator/failover")
    @Operation(summary = "Handle Orchestrator failover event")
    public ResponseEntity<ApiResponse<Void>> handleFailover(@RequestBody Map<String, Object> event) {
        // Parse fields from Orchestrator PostFailoverProcesses webhook
        // Expected fields: ClusterAlias, SuccessorHost, SuccessorPort, FailedHost,
        // FailureType
        String clusterAlias = (String) event.get("ClusterAlias");
        String successorHost = (String) event.get("SuccessorHost");
        String failedHost = (String) event.get("FailedHost");
        String failureType = (String) event.get("FailureType");

        log.info("Failover event: cluster={}, failed={}, successor={}, type={}",
                clusterAlias, failedHost, successorHost, failureType);

        // Extract cluster ID from ClusterAlias (format: mysql-{clusterId})
        String clusterId = null;
        if (clusterAlias != null && clusterAlias.startsWith("mysql-")) {
            clusterId = clusterAlias.replace("mysql-", "");
        } else {
            // Fallback: try to extract from SuccessorHost or FailedHost
            clusterId = failoverService.extractClusterIdFromHostname(successorHost);
            if (clusterId == null) {
                clusterId = failoverService.extractClusterIdFromHostname(failedHost);
            }
        }

        if (clusterId == null) {
            log.warn("Could not extract cluster ID from failover event");
            return ResponseEntity.ok(ApiResponse.error("INVALID_PAYLOAD", "Could not determine cluster ID"));
        }

        if (successorHost == null || successorHost.isEmpty()) {
            log.warn("No successor host in failover event");
            return ResponseEntity.ok(ApiResponse.error("INVALID_PAYLOAD", "No successor host specified"));
        }

        try {
            Cluster cluster = clusterService.getClusterInternal(clusterId);

            // Delegate all business logic to FailoverService
            failoverService.handleFailover(cluster, failedHost, successorHost);

            return ResponseEntity.ok(ApiResponse.success("Failover processed successfully"));

        } catch (Exception e) {
            log.error("Failed to handle failover event for cluster {}: {}", clusterId, e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.error("PROCESSING_ERROR", e.getMessage()));
        }
    }

    @PostMapping("/orchestrator/recovery")
    @Operation(summary = "Handle Orchestrator recovery event")
    public ResponseEntity<ApiResponse<Void>> handleRecovery(@RequestBody Map<String, Object> event) {
        log.debug("Received recovery event: {}", event);
        return ResponseEntity.ok(ApiResponse.success("Recovery event received"));
    }

    @PostMapping("/prometheus/alert")
    @Operation(summary = "Handle Prometheus alert")
    public ResponseEntity<ApiResponse<Void>> handlePrometheusAlert(@RequestBody Map<String, Object> alert) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> labels = (Map<String, Object>) alert.get("labels");
            String alertName = labels != null ? (String) labels.get("alertname") : null;

            if ("HighCPUUsage".equals(alertName)) {
                notificationService.notifyTelegram("⚠️ High CPU Alert",
                        "One or more clusters are experiencing high CPU usage.");
            } else if ("HighMemoryUsage".equals(alertName)) {
                notificationService.notifyTelegram("⚠️ High Memory Alert",
                        "One or more clusters are experiencing high memory usage.");
            }

            return ResponseEntity.ok(ApiResponse.success("Alert processed"));

        } catch (Exception e) {
            log.warn("Failed to process Prometheus alert: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.error("PROCESSING_ERROR", e.getMessage()));
        }
    }

    private String extractClusterId(Map<String, Object> event) {
        // Try different fields for cluster identification
        String analysisHost = (String) event.get("AnalysisInstanceKey");
        if (analysisHost == null) {
            analysisHost = (String) event.get("FailedInstanceKey");
        }

        if (analysisHost != null && analysisHost.contains("mysql-")) {
            // Extract cluster ID from hostname like "mysql-abc123-master:3306"
            String hostname = analysisHost.split(":")[0];
            String[] parts = hostname.split("-");
            if (parts.length >= 2) {
                return parts[1];
            }
        }
        return null;
    }
}
