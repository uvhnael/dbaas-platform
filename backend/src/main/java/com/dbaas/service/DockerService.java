package com.dbaas.service;

import com.dbaas.exception.DockerOperationException;
import com.dbaas.model.Cluster;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.*;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Service for Docker container management.
 * Handles creation, configuration, and lifecycle of MySQL containers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DockerService {

    private final DockerClient dockerClient;

    @Lazy
    @Autowired
    private SecretService secretService;

    @Value("${cluster.defaults.mysql-version:8.0}")
    private String defaultMysqlVersion;

    @Value("${cluster.defaults.memory-limit:4G}")
    private String defaultMemoryLimit;

    /**
     * Create a MySQL container (master or replica) with custom resource
     * configuration.
     *
     * @param clusterId    The cluster identifier
     * @param role         The role of the container (master or replica-N)
     * @param mysqlVersion The MySQL version to use
     * @param networkId    The Docker network to attach to
     * @param cpuCores     Number of CPU cores for the container
     * @param memory       Memory limit (e.g., "4G", "8G")
     * @param storage      Storage size for data volume (e.g., "10G", "100G")
     * @return Container ID
     */
    public String createMySQLContainer(
            String clusterId,
            String role,
            String mysqlVersion,
            String networkId,
            int cpuCores,
            String memory,
            String storage) {

        String containerName = String.format("mysql-%s-%s", clusterId, role);
        String imageName = "mysql:" + mysqlVersion;

        log.info("Creating MySQL container: {} with {} vCPU, {} RAM, {} storage",
                containerName, cpuCores, memory, storage);

        // 1. Pull image if not exists
        pullImageIfNeeded(imageName);

        // 2. Server ID & Password
        int serverId = role.equals("master") ? 1 : Integer.parseInt(role.replace("replica-", "")) + 1;
        String rootPassword = secretService != null
                ? secretService.generateMySQLRootPassword(clusterId)
                : "root_" + clusterId + "_pwd";

        Map<String, String> envVars = Map.of(
                "MYSQL_ROOT_PASSWORD", rootPassword,
                "MYSQL_DATABASE", "appdb");

        // 3. Define Volume Path
        String hostDataPath = "/opt/dbaas/clusters/" + clusterId + "/" + role;

        // Cấu hình Healthcheck để Docker tự biết khi nào DB thực sự "Up"
        HealthCheck healthCheck = new HealthCheck()
                .withTest(List.of("CMD-SHELL", "mysqladmin ping -h localhost -uroot -p" + rootPassword))
                .withInterval(10_000_000_000L) // 10 giây
                .withTimeout(5_000_000_000L) // 5 giây
                .withRetries(3);

        // Calculate CPU quota (1 core = 100000 microseconds per 100ms period)
        long cpuPeriod = 100000L; // 100ms in microseconds
        long cpuQuota = cpuCores * cpuPeriod;

        // 4. Create container with custom resources
        CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
                .withName(containerName)
                .withHostName(containerName)
                .withEnv(envVars.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .toList())
                .withLabels(Map.of(
                        "dbaas.cluster.id", clusterId,
                        "dbaas.role", role,
                        "dbaas.managed", "true",
                        "dbaas.cpu", String.valueOf(cpuCores),
                        "dbaas.memory", memory,
                        "dbaas.storage", storage))
                .withHealthcheck(healthCheck)
                .withHostConfig(HostConfig.newHostConfig()
                        .withNetworkMode(networkId)
                        .withMemory(parseMemory(memory))
                        .withMemorySwap(parseMemory(memory)) // Disable swap
                        .withCpuPeriod(cpuPeriod)
                        .withCpuQuota(cpuQuota)
                        .withBinds(Bind.parse(hostDataPath + ":/var/lib/mysql"))
                        .withRestartPolicy(RestartPolicy.unlessStoppedRestart()))
                .withCmd(
                        "--server-id=" + serverId,
                        "--log-bin=mysql-bin",
                        "--binlog-format=ROW",
                        "--gtid-mode=ON",
                        "--enforce-gtid-consistency=ON",
                        "--log-slave-updates=ON",
                        "--report-host=" + containerName,
                        "--report-port=3306",
                        "--character-set-server=utf8mb4",
                        "--collation-server=utf8mb4_unicode_ci",
                        // Configure InnoDB buffer pool based on available memory
                        "--innodb-buffer-pool-size=" + calculateBufferPoolSize(memory),
                        role.equals("master") ? "--read-only=OFF" : "--read-only=ON")
                .exec();

        // 5. Start container
        dockerClient.startContainerCmd(container.getId()).exec();

        log.info("MySQL container '{}' started with ID: {}. Resources: {} vCPU, {} RAM. Data: {}",
                containerName, container.getId(), cpuCores, memory, hostDataPath);

        return container.getId();
    }

    /**
     * Create a MySQL container with default resources (backward compatibility).
     */
    public String createMySQLContainer(
            String clusterId,
            String role,
            String mysqlVersion,
            String networkId) {
        return createMySQLContainer(clusterId, role, mysqlVersion, networkId, 2, defaultMemoryLimit, "10G");
    }

    /**
     * Calculate InnoDB buffer pool size (typically 70-80% of available memory).
     */
    private String calculateBufferPoolSize(String memory) {
        long bytes = parseMemory(memory);
        // Use 70% of memory for buffer pool
        long bufferPoolBytes = (long) (bytes * 0.7);
        // Convert to MB for MySQL config
        long bufferPoolMB = bufferPoolBytes / (1024 * 1024);
        return bufferPoolMB + "M";
    }

    /**
     * Create ProxySQL container for load balancing.
     * Exposes ports for external client connections.
     * 
     * @param clusterId The cluster identifier
     * @param networkId The Docker network to attach to
     * @return Container ID
     */
    public String createProxySQLContainer(String clusterId, String networkId) {
        String containerName = "proxysql-" + clusterId;
        String imageName = "proxysql/proxysql:2.5.5";

        log.info("Creating ProxySQL container: {}", containerName);

        pullImageIfNeeded(imageName);

        // Calculate unique host ports based on cluster ID hash
        int portOffset = Math.abs(clusterId.hashCode() % 1000);
        int hostClientPort = 16033 + portOffset;
        int hostAdminPort = 16032 + portOffset;

        // Port bindings for external access
        Ports portBindings = new Ports();
        portBindings.bind(ExposedPort.tcp(6033), Ports.Binding.bindPort(hostClientPort));
        portBindings.bind(ExposedPort.tcp(6032), Ports.Binding.bindPort(hostAdminPort));

        CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
                .withName(containerName)
                .withHostName(containerName)
                .withExposedPorts(ExposedPort.tcp(6033), ExposedPort.tcp(6032))
                .withLabels(Map.of(
                        "dbaas.cluster.id", clusterId,
                        "dbaas.role", "proxysql",
                        "dbaas.managed", "true",
                        "dbaas.port.client", String.valueOf(hostClientPort),
                        "dbaas.port.admin", String.valueOf(hostAdminPort)))
                .withHostConfig(HostConfig.newHostConfig()
                        .withNetworkMode(networkId)
                        .withPortBindings(portBindings)
                        .withRestartPolicy(RestartPolicy.unlessStoppedRestart()))
                .exec();

        dockerClient.startContainerCmd(container.getId()).exec();

        log.info("ProxySQL container '{}' started - Client port: {}, Admin port: {}",
                containerName, hostClientPort, hostAdminPort);

        return container.getId();
    }

    // ========================================================================
    // MYSQL CONFIGURATION METHODS
    // ========================================================================

    /**
     * Setup MySQL Primary with all required users.
     * Creates replication user and orchestrator user on master.
     * Uses Resilience4j Retry for non-blocking retry with proper backoff.
     * 
     * @param cluster The cluster to configure
     */
    public void setupPrimary(Cluster cluster) {
        log.info("Setting up MySQL Primary for cluster: {}", cluster.getId());

        String clusterId = cluster.getId();

        String password = secretService != null
                ? secretService.generateMySQLRootPassword(clusterId)
                : generatePassword(clusterId);

        // Generate unique passwords per cluster using SecretService
        String replicationPassword = secretService != null
                ? secretService.generateReplicationPassword(clusterId)
                : generatePassword(clusterId + ":repl");

        String orchestratorPassword = secretService != null
                ? secretService.generateOrchestratorPassword(clusterId)
                : generatePassword(clusterId + ":orch");

        String appUserPassword = secretService != null
                ? secretService.generateAppUserPassword(clusterId)
                : generatePassword(clusterId + ":app");

        String appUser = cluster.getDbUser() != null ? cluster.getDbUser() : "app_user";

        // Create all required users on primary with unique per-cluster passwords
        // SECURITY: Escape all passwords to prevent SQL injection via special
        // characters
        String sql =
                // Replication User (use mysql_native_password for compatibility)
                "CREATE USER IF NOT EXISTS 'repl'@'%' IDENTIFIED WITH mysql_native_password BY '"
                        + escapeSqlString(replicationPassword) + "';" +
                        "GRANT REPLICATION SLAVE ON *.* TO 'repl'@'%';" +
                        // Orchestrator User (must match orchestrator.conf.json)
                        "CREATE USER IF NOT EXISTS 'orchestrator'@'%' IDENTIFIED BY '"
                        + escapeSqlString(orchestratorPassword) + "';" +
                        "GRANT SUPER, PROCESS, REPLICATION SLAVE, REPLICATION CLIENT, RELOAD ON *.* TO 'orchestrator'@'%';"
                        +
                        "GRANT SELECT ON mysql.slave_master_info TO 'orchestrator'@'%';" +
                        // Application User for client connections via ProxySQL
                        "CREATE USER IF NOT EXISTS '" + escapeSqlString(appUser)
                        + "'@'%' IDENTIFIED WITH mysql_native_password BY '" + escapeSqlString(appUserPassword) + "';" +
                        "GRANT ALL PRIVILEGES ON *.* TO '" + escapeSqlString(appUser) + "'@'%';" +
                        "FLUSH PRIVILEGES;";

        // Use Resilience4j Retry instead of Thread.sleep
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(6)
                .waitDuration(Duration.ofSeconds(5))
                .retryExceptions(Exception.class)
                .build();

        Retry retry = Retry.of("setupPrimary-" + cluster.getId(), retryConfig);

        try {
            Retry.decorateRunnable(retry, () -> {
                log.info("Setting up Primary for cluster: {}", cluster.getId());
                execInContainer(cluster.getMasterContainerId(),
                        "mysql", "-uroot", "-p" + password, "-e", sql);
                log.info("MySQL Primary setup completed successfully");
            }).run();
        } catch (Exception e) {
            log.error("Failed to setup MySQL Primary after all retry attempts: {}", e.getMessage());
            throw new DockerOperationException("Failed to setup MySQL Primary", e);
        }
    }

    /**
     * Setup MySQL replication between master and replicas.
     * Iterates through all replicas and configures each one.
     * 
     * @param cluster The cluster to configure replication for
     */
    public void setupReplication(Cluster cluster) {
        log.info("Setting up replication for cluster: {}", cluster.getId());

        for (String replicaId : cluster.getReplicaContainerIds()) {
            setupReplicaReplication(cluster, replicaId);
        }
    }

    /**
     * Setup replication for a single replica.
     * Configures the replica to connect to master using GTID-based replication.
     * 
     * @param cluster            The cluster containing the replica
     * @param replicaContainerId The container ID of the replica to configure
     */
    public void setupReplicaReplication(Cluster cluster, String replicaContainerId) {
        String clusterId = cluster.getId();
        String masterHost = "mysql-" + clusterId + "-master";

        String password = secretService != null
                ? secretService.generateMySQLRootPassword(clusterId)
                : generatePassword(clusterId);

        // Use unique replication password per cluster
        String replicationPassword = secretService != null
                ? secretService.generateReplicationPassword(clusterId)
                : generatePassword(clusterId + ":repl");

        // SECURITY: Escape password to prevent SQL injection
        String replicationSql = String.format(
                "STOP REPLICA; " +
                        "RESET SLAVE ALL; " +
                        "RESET MASTER; " +
                        "CHANGE REPLICATION SOURCE TO " +
                        "SOURCE_HOST='%s', " +
                        "SOURCE_USER='repl', " +
                        "SOURCE_PASSWORD='%s', " +
                        "SOURCE_AUTO_POSITION=1; " +
                        "START REPLICA;",
                escapeSqlString(masterHost), escapeSqlString(replicationPassword));

        execInContainer(replicaContainerId, "mysql", "-uroot", "-p" + password, "-e", replicationSql);
        log.info("Replication configured for replica: {}", replicaContainerId);
    }

    // ========================================================================
    // CONTAINER LIFECYCLE METHODS
    // ========================================================================

    /**
     * Check if container is running and healthy.
     * 
     * @param containerId The container ID to check
     * @return true if container is running, false otherwise
     */
    public boolean isContainerHealthy(String containerId) {
        try {
            InspectContainerResponse info = dockerClient.inspectContainerCmd(containerId).exec();
            InspectContainerResponse.ContainerState state = info.getState();

            // 1. Kiểm tra nếu container không còn chạy
            if (!Boolean.TRUE.equals(state.getRunning())) {
                return false;
            }

            // 2. Kiểm tra Health Status từ Docker (do ta đã cấu hình .withHealthcheck)
            // Các trạng thái có thể có: "starting", "healthy", "unhealthy"
            if (state.getHealth() != null) {
                String status = state.getHealth().getStatus();
                log.debug("Container {} health status: {}", containerId, status);
                return "healthy".equals(status);
            }

            // 3. Dự phòng: Nếu container không có cấu hình healthcheck,
            // thì ít nhất nó phải đang chạy và không ở trạng thái Restarting
            return Boolean.TRUE.equals(state.getRunning()) && !Boolean.TRUE.equals(state.getRestarting());

        } catch (Exception e) {
            log.warn("Không thể kiểm tra sức khỏe container: {}", containerId);
            return false;
        }
    }

    /**
     * Get container IP address by container ID.
     */
    public String getContainerIp(String containerId) {
        try {
            InspectContainerResponse info = dockerClient.inspectContainerCmd(containerId).exec();
            var networks = info.getNetworkSettings().getNetworks();
            if (networks != null && !networks.isEmpty()) {
                // Get IP from the first network
                for (var network : networks.values()) {
                    String ip = network.getIpAddress();
                    if (ip != null && !ip.isEmpty()) {
                        return ip;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to get container IP: {}", containerId, e);
        }
        return null;
    }

    /**
     * Get container IP address by container name.
     */
    public String getContainerIpByName(String containerName) {
        try {
            var containers = dockerClient.listContainersCmd()
                    .withNameFilter(List.of(containerName))
                    .exec();
            if (!containers.isEmpty()) {
                return getContainerIp(containers.get(0).getId());
            }
        } catch (Exception e) {
            log.error("Failed to get container IP by name: {}", containerName, e);
        }
        return null;
    }

    /**
     * Get the published (host) port for a container's internal port.
     * 
     * @param containerName The container name
     * @param internalPort  The internal port to look up
     * @return The published host port, or null if not found
     */
    public Integer getPublishedPort(String containerName, int internalPort) {
        try {
            var containers = dockerClient.listContainersCmd()
                    .withNameFilter(List.of(containerName))
                    .exec();
            if (!containers.isEmpty()) {
                var container = containers.get(0);
                var ports = container.getPorts();
                if (ports != null) {
                    for (var port : ports) {
                        if (port.getPrivatePort() != null && port.getPrivatePort() == internalPort) {
                            return port.getPublicPort();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to get published port for container {}: {}", containerName, e.getMessage());
        }
        return null;
    }

    /**
     * Start a container.
     */
    public void startContainer(String containerId) {
        try {
            dockerClient.startContainerCmd(containerId).exec();
            log.info("Container started: {}", containerId);
        } catch (Exception e) {
            log.error("Failed to start container: {}", containerId, e);
            throw new DockerOperationException("startContainer", containerId, e.getMessage());
        }
    }

    /**
     * Stop a container.
     */
    public void stopContainer(String containerId) {
        try {
            dockerClient.stopContainerCmd(containerId).exec();
            log.info("Container stopped: {}", containerId);
        } catch (Exception e) {
            log.error("Failed to stop container: {}", containerId, e);
            throw new DockerOperationException("stopContainer", containerId, e.getMessage());
        }
    }

    /**
     * Remove a container.
     */
    public void removeContainer(String containerId) {
        try {
            dockerClient.stopContainerCmd(containerId).exec();
        } catch (Exception e) {
            log.warn("Container already stopped: {}", containerId);
        }

        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
            log.info("Container removed: {}", containerId);
        } catch (Exception e) {
            log.error("Failed to remove container: {}", containerId, e);
        }
    }

    /**
     * Execute command inside container.
     */
    public String execInContainer(String containerId, String... command) {
        try {
            var execCreate = dockerClient.execCreateCmd(containerId)
                    .withCmd(command)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();

            dockerClient.execStartCmd(execCreate.getId()).start().awaitCompletion();

            return "OK";
        } catch (Exception e) {
            log.error("Exec failed in container {}: {}", containerId, e.getMessage());
            throw new DockerOperationException("execInContainer", containerId, e.getMessage());
        }
    }

    /**
     * Get container logs.
     */
    public String getContainerLogs(String containerId, int lines) {
        StringBuilder logs = new StringBuilder();

        try {
            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(lines)
                    .exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            logs.append(new String(frame.getPayload()));
                        }
                    })
                    .awaitCompletion();
        } catch (Exception e) {
            log.error("Failed to get logs for container: {}", containerId);
        }

        return logs.toString();
    }

    /**
     * Get container logs with timestamps.
     */
    public String getContainerLogs(String containerId, int lines, boolean timestamps) {
        StringBuilder logs = new StringBuilder();

        try {
            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(lines)
                    .withTimestamps(timestamps)
                    .exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            logs.append(new String(frame.getPayload()));
                        }
                    })
                    .awaitCompletion();
        } catch (Exception e) {
            log.error("Failed to get logs for container: {}", containerId);
        }

        return logs.toString();
    }

    /**
     * Get real-time container statistics (CPU, memory, network, disk I/O).
     */
    public com.dbaas.model.dto.NodeStatsResponse getContainerStats(String containerId, String nodeId) {
        try {
            InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerId).exec();
            boolean isRunning = inspect.getState().getRunning() != null && inspect.getState().getRunning();
            String state = inspect.getState().getStatus();

            if (!isRunning) {
                return com.dbaas.model.dto.NodeStatsResponse.builder()
                        .nodeId(nodeId)
                        .containerName(inspect.getName().replaceFirst("/", ""))
                        .timestamp(java.time.Instant.now())
                        .running(false)
                        .state(state)
                        .build();
            }

            // Use a single-shot stats call
            final com.dbaas.model.dto.NodeStatsResponse.NodeStatsResponseBuilder statsBuilder = com.dbaas.model.dto.NodeStatsResponse
                    .builder()
                    .nodeId(nodeId)
                    .containerName(inspect.getName().replaceFirst("/", ""))
                    .timestamp(java.time.Instant.now())
                    .running(true)
                    .state(state);

            dockerClient.statsCmd(containerId)
                    .withNoStream(true)
                    .exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<Statistics>() {
                        @Override
                        public void onNext(Statistics stats) {
                            // CPU calculation
                            long cpuDelta = stats.getCpuStats().getCpuUsage().getTotalUsage() -
                                    stats.getPreCpuStats().getCpuUsage().getTotalUsage();
                            long systemDelta = stats.getCpuStats().getSystemCpuUsage() -
                                    stats.getPreCpuStats().getSystemCpuUsage();
                            int cpuCount = stats.getCpuStats().getCpuUsage().getPercpuUsage() != null
                                    ? stats.getCpuStats().getCpuUsage().getPercpuUsage().size()
                                    : 1;
                            double cpuPercent = systemDelta > 0
                                    ? ((double) cpuDelta / systemDelta) * cpuCount * 100.0
                                    : 0.0;

                            // Memory
                            long memUsage = stats.getMemoryStats().getUsage() != null
                                    ? stats.getMemoryStats().getUsage()
                                    : 0;
                            long memLimit = stats.getMemoryStats().getLimit() != null
                                    ? stats.getMemoryStats().getLimit()
                                    : 1;
                            double memPercent = (double) memUsage / memLimit * 100.0;

                            // Network I/O
                            long rxBytes = 0, txBytes = 0;
                            if (stats.getNetworks() != null) {
                                for (var network : stats.getNetworks().values()) {
                                    rxBytes += network.getRxBytes();
                                    txBytes += network.getTxBytes();
                                }
                            }

                            // Block I/O
                            long blockRead = 0, blockWrite = 0;
                            if (stats.getBlkioStats() != null
                                    && stats.getBlkioStats().getIoServiceBytesRecursive() != null) {
                                for (var io : stats.getBlkioStats().getIoServiceBytesRecursive()) {
                                    if ("Read".equalsIgnoreCase(io.getOp())) {
                                        blockRead += io.getValue();
                                    } else if ("Write".equalsIgnoreCase(io.getOp())) {
                                        blockWrite += io.getValue();
                                    }
                                }
                            }

                            statsBuilder
                                    .cpuUsagePercent(Math.round(cpuPercent * 100.0) / 100.0)
                                    .memoryUsage(memUsage)
                                    .memoryLimit(memLimit)
                                    .memoryUsagePercent(Math.round(memPercent * 100.0) / 100.0)
                                    .networkRxBytes(rxBytes)
                                    .networkTxBytes(txBytes)
                                    .blockRead(blockRead)
                                    .blockWrite(blockWrite);
                        }
                    })
                    .awaitCompletion();

            return statsBuilder.build();

        } catch (Exception e) {
            log.error("Failed to get stats for container: {}", containerId, e);
            return com.dbaas.model.dto.NodeStatsResponse.builder()
                    .nodeId(nodeId)
                    .containerName(containerId)
                    .timestamp(java.time.Instant.now())
                    .running(false)
                    .state("error")
                    .build();
        }
    }

    // === Private helper methods ===

    private void pullImageIfNeeded(String imageName) {
        try {
            dockerClient.inspectImageCmd(imageName).exec();
        } catch (Exception e) {
            log.info("Pulling image: {}", imageName);
            try {
                dockerClient.pullImageCmd(imageName)
                        .start()
                        .awaitCompletion();
            } catch (Exception ex) {
                log.error("Failed to pull image: {}", imageName);
            }
        }
    }

    private String generatePassword(String clusterId) {
        // In production, use proper secret management
        return "root_" + clusterId + "_pwd";
    }

    /**
     * Escape single quotes in SQL strings to prevent SQL injection.
     * Used when concatenating values into SQL statements executed via mysql CLI.
     *
     * @param input The string to escape
     * @return Escaped string safe for SQL
     */
    private String escapeSqlString(String input) {
        if (input == null) {
            return null;
        }
        // Escape single quotes by doubling them (SQL standard)
        // Also escape backslashes which MySQL treats specially
        return input.replace("\\", "\\\\").replace("'", "''");
    }

    private long parseMemory(String memory) {
        String value = memory.toUpperCase();
        if (value.endsWith("G")) {
            return Long.parseLong(value.replace("G", "")) * 1024 * 1024 * 1024;
        }
        if (value.endsWith("M")) {
            return Long.parseLong(value.replace("M", "")) * 1024 * 1024;
        }
        return Long.parseLong(value);
    }
}
