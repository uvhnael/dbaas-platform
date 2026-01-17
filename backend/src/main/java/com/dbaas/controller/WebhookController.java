package com.dbaas.controller;

import com.dbaas.model.Cluster;
import com.dbaas.model.Node;
import com.dbaas.model.NodeStatus;
import com.dbaas.model.dto.ApiResponse;
import com.dbaas.repository.NodeRepository;
import com.dbaas.service.ClusterService;
import com.dbaas.service.NotificationService;
import com.dbaas.service.ProxySQLService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

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
    private final ProxySQLService proxySQLService;
    private final NotificationService notificationService;
    private final NodeRepository nodeRepository;

    /**
     * Handle Orchestrator topology-recovery webhook.
     * Called when Orchestrator performs a failover.
     */
    @PostMapping("/orchestrator/topology-recovery")
    @Operation(summary = "Handle Orchestrator topology recovery event")
    public ResponseEntity<ApiResponse<Void>> handleTopologyRecovery(@RequestBody Map<String, Object> event) {
        log.info("Received topology-recovery event: {}", event);

        try {
            String clusterId = extractClusterId(event);
            String oldMasterHost = (String) event.get("FailedInstanceKey");
            String newMasterHost = (String) event.get("SuccessorInstanceKey");

            if (clusterId == null) {
                log.warn("Could not extract cluster ID from event");
                return ResponseEntity.ok(ApiResponse.success("Event received but cluster ID not found"));
            }

            Cluster cluster = clusterService.getCluster(clusterId);

            // 1. Update node status in database
            updateNodeStatuses(cluster, oldMasterHost, newMasterHost);

            // 2. Reconfigure ProxySQL to point to new master
            if (newMasterHost != null) {
                proxySQLService.updateMaster(cluster, newMasterHost);
            }

            // 3. Notify via WebSocket
            notificationService.notifyFailover(cluster,
                    oldMasterHost != null ? oldMasterHost : "unknown",
                    newMasterHost != null ? newMasterHost : "unknown");

            log.info("Topology recovery handled for cluster {}: {} -> {}",
                    clusterId, oldMasterHost, newMasterHost);

            return ResponseEntity.ok(ApiResponse.success("Topology recovery processed successfully"));

        } catch (Exception e) {
            log.error("Failed to handle topology-recovery event", e);
            return ResponseEntity.ok(ApiResponse.error("PROCESSING_ERROR", e.getMessage()));
        }
    }

    @PostMapping("/orchestrator/failover")
    @Operation(summary = "Handle Orchestrator failover event")
    public ResponseEntity<ApiResponse<Void>> handleFailover(@RequestBody Map<String, Object> event) {
        log.info("Received failover event: {}", event);

        String clusterId = extractClusterId(event);
        String newMasterHost = (String) event.get("SuccessorHost");

        if (clusterId != null && newMasterHost != null) {
            try {
                Cluster cluster = clusterService.getCluster(clusterId);

                // Update ProxySQL to point to new master
                proxySQLService.updateMaster(cluster, newMasterHost);

                // Notify via WebSocket
                notificationService.notifyFailover(cluster, "previous-master", newMasterHost);

                log.info("Failover handled for cluster: {}, new master: {}",
                        clusterId, newMasterHost);

                return ResponseEntity.ok(ApiResponse.success("Failover processed successfully"));

            } catch (Exception e) {
                log.error("Failed to handle failover event", e);
                return ResponseEntity.ok(ApiResponse.error("PROCESSING_ERROR", e.getMessage()));
            }
        }

        return ResponseEntity.ok(ApiResponse.success("Failover event received"));
    }

    @PostMapping("/orchestrator/recovery")
    @Operation(summary = "Handle Orchestrator recovery event")
    public ResponseEntity<ApiResponse<Void>> handleRecovery(@RequestBody Map<String, Object> event) {
        log.info("Received recovery event: {}", event);
        return ResponseEntity.ok(ApiResponse.success("Recovery event received"));
    }

    @PostMapping("/prometheus/alert")
    @Operation(summary = "Handle Prometheus alert")
    public ResponseEntity<ApiResponse<Void>> handlePrometheusAlert(@RequestBody Map<String, Object> alert) {
        log.info("Received Prometheus alert: {}", alert);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> labels = (Map<String, Object>) alert.get("labels");
            String alertName = labels != null ? (String) labels.get("alertname") : null;

            if ("HighCPUUsage".equals(alertName)) {
                log.info("High CPU alert - consider scaling");
                notificationService.notifyTelegram("⚠️ High CPU Alert",
                        "One or more clusters are experiencing high CPU usage.");
            } else if ("HighMemoryUsage".equals(alertName)) {
                log.info("High Memory alert - consider scaling");
                notificationService.notifyTelegram("⚠️ High Memory Alert",
                        "One or more clusters are experiencing high memory usage.");
            }

            return ResponseEntity.ok(ApiResponse.success("Alert processed"));

        } catch (Exception e) {
            log.warn("Failed to process Prometheus alert", e);
            return ResponseEntity.ok(ApiResponse.error("PROCESSING_ERROR", e.getMessage()));
        }
    }

    /**
     * Update node statuses after failover.
     */
    private void updateNodeStatuses(Cluster cluster, String oldMasterHost, String newMasterHost) {
        // Update old master to FAILED status
        if (oldMasterHost != null) {
            Optional<Node> oldMaster = nodeRepository.findByContainerName(oldMasterHost);
            oldMaster.ifPresent(node -> {
                node.setStatus(NodeStatus.FAILED);
                nodeRepository.save(node);
                notificationService.notifyNodeStatus(node);
                log.info("Marked old master {} as FAILED", oldMasterHost);
            });
        }

        // Update new master to RUNNING status
        if (newMasterHost != null) {
            Optional<Node> newMaster = nodeRepository.findByContainerName(newMasterHost);
            newMaster.ifPresent(node -> {
                node.setStatus(NodeStatus.RUNNING);
                nodeRepository.save(node);
                notificationService.notifyNodeStatus(node);
                log.info("Marked new master {} as RUNNING", newMasterHost);
            });
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
