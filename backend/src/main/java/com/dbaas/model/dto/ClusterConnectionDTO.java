package com.dbaas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterConnectionDTO {
    private String clusterId;
    private String clusterName;

    // Connection info
    private String host;
    private Integer port; // ProxySQL port (handles read/write splitting automatically)

    // Credentials
    private String username;
    private String password;
    private String rootPassword; // For admin operations

    // Connection string (pre-built for convenience)
    private String connectionString; // mysql://user:pass@host:port

    // Docker network info (for internal access)
    private String proxyContainerName;
    private String dockerNetwork;
}
