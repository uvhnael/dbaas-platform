package com.dbaas.service;

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
            }

        } catch (Exception e) {
            log.error("Failed to register cluster with Orchestrator: {}", cluster.getId(), e);
        }
    }

    /**
     * Unregister a cluster from Orchestrator.
     */
    public void unregisterCluster(Cluster cluster) {
        log.info("Unregistering cluster from Orchestrator: {}", cluster.getId());

        String masterHost = "mysql-" + cluster.getId() + "-master";
        String forgetUrl = orchestratorUrl + "/api/forget/" + masterHost + "/3306";

        try {
            restTemplate.getForEntity(forgetUrl, String.class);
            log.info("Cluster unregistered from Orchestrator: {}", cluster.getId());
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
