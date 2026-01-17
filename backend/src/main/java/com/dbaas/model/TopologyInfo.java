package com.dbaas.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Topology information from Orchestrator.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopologyInfo {

    private String clusterName;
    private String masterHost;
    private int masterPort;
    private List<ReplicaInfo> replicas;
    private boolean healthy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReplicaInfo {
        private String host;
        private int port;
        private long secondsBehindMaster;
        private boolean replicating;
    }
}
