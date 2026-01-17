package com.dbaas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for cluster-wide logs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterLogsResponse {

    private String clusterId;
    private List<NodeLogsResponse> nodes;
}
