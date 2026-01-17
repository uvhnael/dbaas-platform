package com.dbaas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for dashboard summary.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryResponse {

    private Instant timestamp;
    
    // Cluster counts
    private int totalClusters;
    private int runningClusters;
    private int stoppedClusters;
    private int failedClusters;

    // Node counts
    private int totalNodes;
    private int healthyNodes;
    private int unhealthyNodes;

    // Resource usage (aggregated)
    private ResourceUsage resourceUsage;

    // Recent activity
    private List<ClusterSummary> clusters;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceUsage {
        private double avgCpuPercent;
        private long totalMemoryUsage;
        private long totalMemoryLimit;
        private double avgMemoryPercent;
        private long totalNetworkRx;
        private long totalNetworkTx;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClusterSummary {
        private String clusterId;
        private String name;
        private String status;
        private int nodeCount;
        private int healthyNodeCount;
    }
}
