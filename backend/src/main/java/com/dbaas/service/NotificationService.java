package com.dbaas.service;

import com.dbaas.model.Cluster;
import com.dbaas.model.Node;
import com.dbaas.model.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

/**
 * Service for sending real-time notifications via WebSocket and external
 * webhooks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${n8n.webhook-url:}")
    private String n8nWebhookUrl;

    /**
     * Send cluster status update via WebSocket.
     */
    public void notifyClusterStatus(Cluster cluster) {
        String destination = "/topic/clusters/" + cluster.getId();

        Map<String, Object> payload = Map.of(
                "type", "CLUSTER_STATUS",
                "clusterId", cluster.getId(),
                "clusterName", cluster.getName(),
                "status", cluster.getStatus().name(),
                "timestamp", Instant.now().toString());

        messagingTemplate.convertAndSend(destination, payload);
        log.debug("Sent cluster status update to {}: {}", destination, cluster.getStatus());
    }

    /**
     * Send task progress update via WebSocket.
     */
    public void notifyTaskProgress(Task task, String message) {
        String destination = "/topic/tasks/" + task.getId();

        Map<String, Object> payload = Map.of(
                "type", "TASK_PROGRESS",
                "taskId", task.getId(),
                "clusterId", task.getClusterId() != null ? task.getClusterId() : "",
                "taskType", task.getType().name(),
                "status", task.getStatus().name(),
                "message", message,
                "timestamp", Instant.now().toString());

        messagingTemplate.convertAndSend(destination, payload);

        // Also send to cluster topic if associated
        if (task.getClusterId() != null) {
            messagingTemplate.convertAndSend("/topic/clusters/" + task.getClusterId(), payload);
        }

        log.debug("Sent task progress to {}: {}", destination, message);
    }

    /**
     * Send failover notification via WebSocket.
     */
    public void notifyFailover(Cluster cluster, String oldMaster, String newMaster) {
        String destination = "/topic/clusters/" + cluster.getId();

        Map<String, Object> payload = Map.of(
                "type", "FAILOVER",
                "clusterId", cluster.getId(),
                "clusterName", cluster.getName(),
                "oldMaster", oldMaster,
                "newMaster", newMaster,
                "timestamp", Instant.now().toString());

        messagingTemplate.convertAndSend(destination, payload);
        log.info("Sent failover notification for cluster {}: {} -> {}",
                cluster.getId(), oldMaster, newMaster);

        // Also notify via external webhook (Telegram)
        notifyTelegram("⚠️ Failover Alert",
                String.format("Cluster: %s\nOld Master: %s\nNew Master: %s",
                        cluster.getName(), oldMaster, newMaster));
    }

    /**
     * Send node status update via WebSocket.
     */
    public void notifyNodeStatus(Node node) {
        if (node.getClusterId() == null)
            return;

        String destination = "/topic/clusters/" + node.getClusterId();

        Map<String, Object> payload = Map.of(
                "type", "NODE_STATUS",
                "clusterId", node.getClusterId(),
                "nodeId", node.getId(),
                "containerName", node.getContainerName(),
                "role", node.getRole().name(),
                "status", node.getStatus().name(),
                "timestamp", Instant.now().toString());

        messagingTemplate.convertAndSend(destination, payload);
        log.debug("Sent node status update: {} -> {}", node.getContainerName(), node.getStatus());
    }

    /**
     * Broadcast to all connected clients.
     */
    public void broadcast(String type, String message) {
        Map<String, Object> payload = Map.of(
                "type", type,
                "message", message,
                "timestamp", Instant.now().toString());

        messagingTemplate.convertAndSend("/topic/broadcast", payload);
        log.debug("Broadcast message: {}", message);
    }

    /**
     * Send notification to n8n webhook (for Telegram alerts).
     */
    @Async
    public void notifyTelegram(String title, String message) {
        if (n8nWebhookUrl == null || n8nWebhookUrl.isBlank()) {
            log.debug("N8N webhook URL not configured, skipping Telegram notification");
            return;
        }

        try {
            Map<String, String> payload = Map.of(
                    "title", title,
                    "message", message,
                    "timestamp", Instant.now().toString());

            restTemplate.postForEntity(n8nWebhookUrl, payload, String.class);
            log.info("Sent Telegram notification via n8n: {}", title);

        } catch (Exception e) {
            log.warn("Failed to send Telegram notification: {}", e.getMessage());
        }
    }
}
