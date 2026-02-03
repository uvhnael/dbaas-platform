package com.dbaas.service;

import com.dbaas.exception.OrchestratorException;
import com.dbaas.model.Cluster;
import com.dbaas.model.TopologyInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Service for Orchestrator API integration.
 * Handles cluster registration and failover management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrchestratorService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${orchestrator.url:http://orchestrator:3000}")
    private String orchestratorUrl;

    /**
     * Register a cluster with Orchestrator for monitoring.
     * Throws RuntimeException on failure to enable retry logic.
     */
    public void registerCluster(Cluster cluster) {
        log.info("Registering cluster with Orchestrator: {}", cluster.getId());

        String masterHost = "mysql-" + cluster.getId() + "-master";
        String discoverUrl = orchestratorUrl + "/api/discover/" + masterHost + "/3306";

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(discoverUrl, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Cluster registered with Orchestrator: {}", cluster.getId());
            } else {
                log.warn("Orchestrator registration returned: {}", response.getStatusCode());
                throw new OrchestratorException("registerCluster", cluster.getId(),
                        "Registration failed with status: " + response.getStatusCode());
            }

        } catch (OrchestratorException e) {
            throw e; // Re-throw our custom exception
        } catch (Exception e) {
            log.error("Failed to register cluster with Orchestrator: {}", cluster.getId(), e);
            throw new OrchestratorException("registerCluster", e);
        }
    }

    /**
     * Unregister a cluster from Orchestrator.
     */
    public void unregisterCluster(Cluster cluster) {
        log.info("Unregistering cluster from Orchestrator: {}", cluster.getId());
        for (int i = 0; i < cluster.getReplicaCount(); i++) {
            String replicaHost = "mysql-" + cluster.getId() + "-replica-" + i;
            unregisterNode(replicaHost);
        }
        String masterHost = "mysql-" + cluster.getId() + "-master";
        unregisterNode(masterHost);

    }

    public void unregisterNode(String nodeName) {
        log.info("Unregistering node from Orchestrator: {}", nodeName);

        String forgetUrl = orchestratorUrl + "/api/forget/" + nodeName + "/3306";

        try {
            restTemplate.getForEntity(forgetUrl, String.class);
            log.info("Node unregistered from Orchestrator: {}", nodeName);
        } catch (Exception e) {
            log.warn("Failed to unregister from Orchestrator: {}", e.getMessage());
        }
    }

    /**
     * Get topology information for a cluster.
     */
    public TopologyInfo getTopology(String clusterAlias) {
        String topologyUrl = orchestratorUrl + "/api/cluster/alias/" + clusterAlias;

        try {
            ResponseEntity<TopologyInfo> response = restTemplate.getForEntity(
                    topologyUrl, TopologyInfo.class);
            return response.getBody();

        } catch (Exception e) {
            log.error("Failed to get topology for cluster: {}", clusterAlias, e);
            return null;
        }
    }

    /**
     * Trigger manual failover for a cluster.
     */
    public void triggerFailover(Cluster cluster) {
        log.info("Triggering failover for cluster: {}", cluster.getId());

        String masterHost = "mysql-" + cluster.getId() + "-master";
        String failoverUrl = orchestratorUrl + "/api/graceful-master-takeover-auto/"
                + masterHost + "/3306";

        try {
            restTemplate.getForEntity(failoverUrl, String.class);
            log.info("Failover triggered for cluster: {}", cluster.getId());
        } catch (Exception e) {
            log.error("Failed to trigger failover: {}", e.getMessage());
        }
    }

    /**
     * Check Orchestrator health.
     */
    public boolean isHealthy() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    orchestratorUrl + "/api/health", String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
}
