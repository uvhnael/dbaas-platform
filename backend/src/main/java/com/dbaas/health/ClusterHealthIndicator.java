package com.dbaas.health;

import com.dbaas.repository.ClusterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for cluster management system.
 * Provides overview of managed clusters health.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClusterHealthIndicator implements HealthIndicator {

    private final ClusterRepository clusterRepository;

    @Override
    public Health health() {
        try {
            long totalClusters = clusterRepository.count();
            long runningClusters = clusterRepository.countByStatus(
                    com.dbaas.model.ClusterStatus.RUNNING);
            long failedClusters = clusterRepository.countByStatus(
                    com.dbaas.model.ClusterStatus.FAILED);
            long provisioningClusters = clusterRepository.countByStatus(
                    com.dbaas.model.ClusterStatus.PROVISIONING);

            Health.Builder builder;
            
            if (failedClusters > 0) {
                builder = Health.down()
                        .withDetail("status", "Some clusters have failed");
            } else if (totalClusters == 0 || runningClusters == totalClusters - provisioningClusters) {
                builder = Health.up()
                        .withDetail("status", "All clusters healthy");
            } else {
                builder = Health.status("DEGRADED")
                        .withDetail("status", "Some clusters are not running");
            }

            return builder
                    .withDetail("totalClusters", totalClusters)
                    .withDetail("runningClusters", runningClusters)
                    .withDetail("failedClusters", failedClusters)
                    .withDetail("provisioningClusters", provisioningClusters)
                    .build();

        } catch (Exception e) {
            log.error("Cluster health check failed: {}", e.getMessage());
            return Health.unknown()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
