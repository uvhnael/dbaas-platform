package com.dbaas.service;

import com.dbaas.config.CacheConfig;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Statistics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service for monitoring cluster metrics.
 * Implements real metrics collection from Docker, MySQL, and ProxySQL.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MonitoringService {

    private final DockerService dockerService;
    private final DockerClient dockerClient;
    private final SecretService secretService;

    /**
     * Get overall metrics for a cluster.
     */
    @Cacheable(value = CacheConfig.CLUSTER_METRICS_CACHE, key = "#clusterId")
    public Map<String, Object> getClusterMetrics(String clusterId) {
        Map<String, Object> metrics = new HashMap<>();

        metrics.put("clusterId", clusterId);
        metrics.put("timestamp", System.currentTimeMillis());

        // Collect metrics in parallel for better performance
        CompletableFuture<Double> qpsFuture = CompletableFuture.supplyAsync(() -> getQPS(clusterId));
        CompletableFuture<Integer> connFuture = CompletableFuture.supplyAsync(() -> getConnectionCount(clusterId));
        CompletableFuture<Integer> lagFuture = CompletableFuture.supplyAsync(() -> getReplicationLag(clusterId));

        try {
            metrics.put("queriesPerSecond", qpsFuture.get(10, TimeUnit.SECONDS));
            metrics.put("connections", connFuture.get(10, TimeUnit.SECONDS));
            metrics.put("replicationLag", lagFuture.get(10, TimeUnit.SECONDS));
        } catch (Exception e) {
            log.warn("Failed to collect some metrics for cluster {}: {}", clusterId, e.getMessage());
            metrics.put("queriesPerSecond", 0.0);
            metrics.put("connections", 0);
            metrics.put("replicationLag", 0);
        }

        return metrics;
    }

    /**
     * Get queries per second from ProxySQL.
     */
    public double getQPS(String clusterId) {
        String proxySqlContainer = "proxysql-" + clusterId;

        try {
            // Query ProxySQL stats through admin interface
            String ip = dockerService.getContainerIpByName(proxySqlContainer);
            if (ip == null) {
                log.debug("ProxySQL container not found for cluster: {}", clusterId);
                return 0.0;
            }

            // Connect to ProxySQL admin interface (port 6032)
            // Use radmin user which is configured for remote access
            String url = "jdbc:mariadb://" + ip + ":6032/stats";
            String proxySqlPassword = secretService.getProxySQLAdminPassword();
            try (Connection conn = DriverManager.getConnection(url, "radmin", proxySqlPassword);
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT COALESCE(SUM(Queries), 0) as total_queries FROM stats_mysql_connection_pool")) {

                if (rs.next()) {
                    long totalQueries = rs.getLong("total_queries");
                    // Calculate QPS based on connection pool stats
                    return calculateQPS(clusterId, totalQueries);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to get QPS for cluster {}: {}", clusterId, e.getMessage());
        }

        return 0.0;
    }

    /**
     * Get active connection count from ProxySQL.
     */
    public int getConnectionCount(String clusterId) {
        String proxySqlContainer = "proxysql-" + clusterId;

        try {
            String ip = dockerService.getContainerIpByName(proxySqlContainer);
            if (ip == null) {
                return 0;
            }

            String url = "jdbc:mariadb://" + ip + ":6032/stats";
            String proxySqlPassword = secretService.getProxySQLAdminPassword();
            try (Connection conn = DriverManager.getConnection(url, "radmin", proxySqlPassword);
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT COALESCE(SUM(ConnUsed), 0) as active_connections FROM stats_mysql_connection_pool")) {

                if (rs.next()) {
                    return rs.getInt("active_connections");
                }
            }
        } catch (Exception e) {
            log.debug("Failed to get connection count for cluster {}: {}", clusterId, e.getMessage());
        }

        return 0;
    }

    /**
     * Get replication lag in seconds for a cluster (max across all replicas).
     */
    public int getReplicationLag(String clusterId) {
        int maxLag = 0;

        // Get all replica containers for this cluster
        // Note: replica roles are labeled as "replica-1", "replica-2", etc.
        // So we filter by cluster ID only, then check if role starts with "replica"
        try {
            var containers = dockerClient.listContainersCmd()
                    .withLabelFilter(Map.of("dbaas.cluster.id", clusterId))
                    .exec();

            log.debug("Found {} containers for cluster {}", containers.size(), clusterId);

            for (var container : containers) {
                // Check if this is a replica node (role starts with "replica")
                var labels = container.getLabels();
                String role = labels != null ? labels.get("dbaas.role") : null;
                log.debug("Container {} has role: {}", container.getNames()[0], role);

                if (role == null || !role.startsWith("replica")) {
                    continue;
                }

                Integer lag = getNodeReplicationLag(container.getId(), clusterId);
                log.debug("Replication lag for container {}: {}", container.getNames()[0], lag);
                if (lag != null && lag > maxLag) {
                    maxLag = lag;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to get replication lag for cluster {}: {}", clusterId, e.getMessage());
        }

        return maxLag;
    }

    /**
     * Get replication lag in seconds for a specific node.
     * Queries SHOW REPLICA STATUS on the MySQL replica.
     * 
     * @param containerId Docker container ID
     * @param clusterId   Cluster ID used to generate the correct MySQL root
     *                    password
     */
    public Integer getNodeReplicationLag(String containerId, String clusterId) {
        try {
            String ip = dockerService.getContainerIp(containerId);
            if (ip == null) {
                log.debug("No IP found for container {}", containerId);
                return null;
            }

            String password = secretService.generateMySQLRootPassword(clusterId);
            log.debug("Connecting to MySQL at {} for cluster {} to get replication lag", ip, clusterId);

            String url = "jdbc:mysql://" + ip + ":3306?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=5000";
            try (Connection conn = DriverManager.getConnection(url, "root", password);
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SHOW REPLICA STATUS")) {

                if (rs.next()) {
                    // Check Seconds_Behind_Source (MySQL 8.0+) or Seconds_Behind_Master
                    Object lag = rs.getObject("Seconds_Behind_Source");
                    if (lag == null) {
                        lag = rs.getObject("Seconds_Behind_Master");
                    }

                    if (lag != null) {
                        int lagValue = ((Number) lag).intValue();
                        log.debug("Replication lag for cluster {}: {} seconds", clusterId, lagValue);
                        return lagValue;
                    }
                }
                log.debug("No replication status found for cluster {} (replication may not be configured)", clusterId);
            }
        } catch (Exception e) {
            log.warn("Failed to get replication lag for container {} (cluster {}): {}", containerId, clusterId,
                    e.getMessage());
        }

        return null;
    }

    /**
     * Get container resource usage (real Docker stats).
     */
    @Cacheable(value = CacheConfig.NODE_STATS_CACHE, key = "#containerId")
    public Map<String, Object> getContainerStats(String containerId) {
        Map<String, Object> stats = new HashMap<>();

        try {
            final Map<String, Object> finalStats = stats;

            dockerClient.statsCmd(containerId)
                    .withNoStream(true)
                    .exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<Statistics>() {
                        @Override
                        public void onNext(Statistics dockerStats) {
                            // CPU calculation
                            long cpuDelta = dockerStats.getCpuStats().getCpuUsage().getTotalUsage() -
                                    dockerStats.getPreCpuStats().getCpuUsage().getTotalUsage();
                            long systemDelta = dockerStats.getCpuStats().getSystemCpuUsage() -
                                    dockerStats.getPreCpuStats().getSystemCpuUsage();
                            int cpuCount = dockerStats.getCpuStats().getCpuUsage().getPercpuUsage() != null
                                    ? dockerStats.getCpuStats().getCpuUsage().getPercpuUsage().size()
                                    : 1;
                            double cpuPercent = systemDelta > 0
                                    ? ((double) cpuDelta / systemDelta) * cpuCount * 100.0
                                    : 0.0;

                            // Memory
                            long memUsage = dockerStats.getMemoryStats().getUsage() != null
                                    ? dockerStats.getMemoryStats().getUsage()
                                    : 0;
                            long memLimit = dockerStats.getMemoryStats().getLimit() != null
                                    ? dockerStats.getMemoryStats().getLimit()
                                    : 1;

                            // Network I/O
                            long rxBytes = 0, txBytes = 0;
                            if (dockerStats.getNetworks() != null) {
                                for (var network : dockerStats.getNetworks().values()) {
                                    rxBytes += network.getRxBytes();
                                    txBytes += network.getTxBytes();
                                }
                            }

                            finalStats.put("cpuPercent", Math.round(cpuPercent * 100.0) / 100.0);
                            finalStats.put("memoryUsage", memUsage);
                            finalStats.put("memoryLimit", memLimit);
                            finalStats.put("memoryPercent", (double) memUsage / memLimit * 100.0);
                            finalStats.put("networkRx", rxBytes);
                            finalStats.put("networkTx", txBytes);
                        }
                    })
                    .awaitCompletion();

        } catch (Exception e) {
            log.warn("Failed to get container stats for {}: {}", containerId, e.getMessage());
            stats.put("cpuPercent", 0.0);
            stats.put("memoryUsage", 0L);
            stats.put("memoryLimit", 0L);
            stats.put("networkRx", 0L);
            stats.put("networkTx", 0L);
        }

        return stats;
    }

    /**
     * Get MySQL server status variables.
     */
    public Map<String, Object> getMySQLStatus(String containerId, String clusterId) {
        Map<String, Object> status = new HashMap<>();

        try {
            String ip = dockerService.getContainerIp(containerId);
            if (ip == null) {
                return status;
            }

            String password = secretService.generateMySQLRootPassword(clusterId);
            String url = "jdbc:mysql://" + ip + ":3306?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=5000";
            try (Connection conn = DriverManager.getConnection(url, "root", password);
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SHOW GLOBAL STATUS WHERE Variable_name IN " +
                            "('Threads_connected', 'Threads_running', 'Questions', 'Slow_queries', " +
                            "'Innodb_buffer_pool_read_requests', 'Innodb_buffer_pool_reads')")) {

                while (rs.next()) {
                    status.put(rs.getString("Variable_name"), rs.getString("Value"));
                }
            }
        } catch (Exception e) {
            log.debug("Failed to get MySQL status for container {}: {}", containerId, e.getMessage());
        }

        return status;
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private final Map<String, Long> lastQueryCounts = new HashMap<>();
    private final Map<String, Long> lastQueryTimes = new HashMap<>();

    private double calculateQPS(String clusterId, long currentQueries) {
        long currentTime = System.currentTimeMillis();

        Long lastCount = lastQueryCounts.get(clusterId);
        Long lastTime = lastQueryTimes.get(clusterId);

        lastQueryCounts.put(clusterId, currentQueries);
        lastQueryTimes.put(clusterId, currentTime);

        if (lastCount == null || lastTime == null) {
            return 0.0;
        }

        long timeDiff = currentTime - lastTime;
        if (timeDiff <= 0) {
            return 0.0;
        }

        long queryDiff = currentQueries - lastCount;
        return (queryDiff * 1000.0) / timeDiff; // QPS
    }

}
