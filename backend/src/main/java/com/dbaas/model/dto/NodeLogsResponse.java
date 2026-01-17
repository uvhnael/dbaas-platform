package com.dbaas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for node logs with enhanced details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeLogsResponse {

    private String nodeId;
    private String containerName;
    private String logs;
    private int lineCount;
    private Instant timestamp;

    /**
     * Simple constructor for backward compatibility.
     */
    public NodeLogsResponse(String nodeId, String containerName, String logs) {
        this.nodeId = nodeId;
        this.containerName = containerName;
        this.logs = logs;
        this.lineCount = logs != null ? logs.split("\n").length : 0;
        this.timestamp = Instant.now();
    }
}
