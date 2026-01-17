package com.dbaas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for node container statistics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeStatsResponse {

    private String nodeId;
    private String containerName;
    private String nodeRole;
    private Instant timestamp;

    // CPU stats
    private double cpuUsagePercent;

    // Memory stats
    private long memoryUsage;
    private long memoryLimit;
    private double memoryUsagePercent;

    // Network I/O
    private long networkRxBytes;
    private long networkTxBytes;

    // Block I/O
    private long blockRead;
    private long blockWrite;

    // Container state
    private boolean running;
    private String state;

    // MySQL Replication (for replica nodes)
    private Integer replicationLagSeconds;
}
