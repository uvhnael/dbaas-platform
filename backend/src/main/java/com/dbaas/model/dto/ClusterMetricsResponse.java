package com.dbaas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for aggregated cluster metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterMetricsResponse {

    private String clusterId;
    private String clusterName;
    private String status;
    private Instant timestamp;

    // Aggregated metrics
    private int totalNodes;
    private int runningNodes;
    private double avgCpuPercent;
    private long totalMemoryUsage;
    private long totalMemoryLimit;
    private double avgMemoryPercent;
    private long totalNetworkRx;
    private long totalNetworkTx;
    private long totalBlockRead;
    private long totalBlockWrite;

    // Individual node metrics
    private List<NodeStatsResponse> nodeMetrics;

    // MySQL-specific metrics
    private double queriesPerSecond;
    private int activeConnections;
    private int replicationLagSeconds;
}
