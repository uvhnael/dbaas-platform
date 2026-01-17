package com.dbaas.service;

import com.dbaas.model.Cluster;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    @Value("${cluster.defaults.mysql-version:8.0}")
    private String defaultMysqlVersion;

    @Value("${cluster.defaults.memory-limit:4G}")
    private String defaultMemoryLimit;

    /**
     * Create a MySQL container (master or replica).
     */
    public String createMySQLContainer(
            String clusterId,
            String role,
            String mysqlVersion,
            String networkId) {

        String containerName = String.format("mysql-%s-%s", clusterId, role);
        String imageName = "mysql:" + mysqlVersion;

        log.info("Creating MySQL container: {}", containerName);

        // Pull image if not exists
        pullImageIfNeeded(imageName);

        // Server ID (unique per container)
        int serverId = role.equals("master") ? 1 : Integer.parseInt(role.replace("replica-", "")) + 1;

        // Environment variables
        Map<String, String> envVars = Map.of(
                "MYSQL_ROOT_PASSWORD", generatePassword(clusterId),
                "MYSQL_DATABASE", "appdb");

        // Create container
        CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
                .withName(containerName)
                .withHostName(containerName)
                .withEnv(envVars.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .toList())
                .withLabels(Map.of(
                        "dbaas.cluster.id", clusterId,
                        "dbaas.role", role,
                        "dbaas.managed", "true"))
                .withHostConfig(HostConfig.newHostConfig()
                        .withNetworkMode(networkId)
                        .withMemory(parseMemory(defaultMemoryLimit))
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
                        role.equals("master") ? "--read-only=OFF" : "--read-only=ON")
                .exec();

        // Start container
        dockerClient.startContainerCmd(container.getId()).exec();

        log.info("MySQL container '{}' started with ID: {}", containerName, container.getId());

        return container.getId();
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
     * 
     * @param cluster The cluster to configure
     */
    public void setupPrimary(Cluster cluster) {
        log.info("Setting up MySQL Primary for cluster: {}", cluster.getId());

        String password = generatePassword(cluster.getId());

        // Wait for MySQL to be ready (retry up to 30 seconds)
        int maxRetries = 6;
        int retryDelayMs = 5000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("Setting up Primary (attempt {}/{})", attempt, maxRetries);

                // Create all required users on primary
                String sql =
                        // Replication User (use mysql_native_password for compatibility)
                        "CREATE USER IF NOT EXISTS 'repl'@'%' IDENTIFIED WITH mysql_native_password BY 'repl_password';"
                                +
                                "GRANT REPLICATION SLAVE ON *.* TO 'repl'@'%';" +
                                // Orchestrator User (must match orchestrator.conf.json)
                                "CREATE USER IF NOT EXISTS 'orchestrator'@'%' IDENTIFIED BY 'orch_password';" +
                                "GRANT SUPER, PROCESS, REPLICATION SLAVE, REPLICATION CLIENT, RELOAD ON *.* TO 'orchestrator'@'%';"
                                +
                                "GRANT SELECT ON mysql.slave_master_info TO 'orchestrator'@'%';" +
                                "FLUSH PRIVILEGES;";

                execInContainer(cluster.getMasterContainerId(),
                        "mysql", "-uroot", "-p" + password, "-e", sql);

                log.info("MySQL Primary setup completed successfully");
                return;

            } catch (Exception e) {
                log.warn("Failed to setup Primary (attempt {}): {}", attempt, e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("Failed to setup MySQL Primary after {} attempts", maxRetries);
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
        String masterHost = "mysql-" + cluster.getId() + "-master";
        String password = generatePassword(cluster.getId());

        String replicationSql = String.format(
                "STOP REPLICA; " +
                        "RESET SLAVE ALL; " +
                        "RESET MASTER; " +
                        "CHANGE REPLICATION SOURCE TO " +
                        "SOURCE_HOST='%s', " +
                        "SOURCE_USER='repl', " +
                        "SOURCE_PASSWORD='repl_password', " +
                        "SOURCE_AUTO_POSITION=1; " +
                        "START REPLICA;",
                masterHost);

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
            return Boolean.TRUE.equals(info.getState().getRunning());
        } catch (Exception e) {
            log.warn("Failed to check container health: {}", containerId);
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
     * Start a container.
     */
    public void startContainer(String containerId) {
        try {
            dockerClient.startContainerCmd(containerId).exec();
            log.info("Container started: {}", containerId);
        } catch (Exception e) {
            log.error("Failed to start container: {}", containerId, e);
            throw new RuntimeException("Failed to start container: " + containerId);
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
            throw new RuntimeException("Failed to stop container: " + containerId);
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
            throw new RuntimeException("Exec failed: " + e.getMessage());
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
