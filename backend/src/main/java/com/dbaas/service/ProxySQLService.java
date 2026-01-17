package com.dbaas.service;

import com.dbaas.model.Cluster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;

/**
 * Service for ProxySQL configuration and management.
 * Connects via published ports on localhost.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProxySQLService {

    private final DockerService dockerService;

    @Value("${proxysql.admin.user:radmin}")
    private String adminUser;

    @Value("${proxysql.admin.password:radmin}")
    private String adminPassword;

    private static final int WRITE_HOSTGROUP = 10;
    private static final int READ_HOSTGROUP = 20;

    /**
     * Calculate the published admin port for a cluster.
     */
    private int getPublishedAdminPort(String clusterId) {
        int portOffset = Math.abs(clusterId.hashCode() % 1000);
        return 16032 + portOffset;
    }

    /**
     * Setup remote admin access by configuring radmin credentials.
     * Must be called before other configuration via JDBC.
     * 
     * @param cluster The cluster to configure
     */
    public void setupRemoteAdminAccess(Cluster cluster) {
        log.info("Setting up ProxySQL remote admin access for cluster: {}", cluster.getId());

        String containerId = cluster.getProxySqlContainerId();

        String sql = "UPDATE global_variables SET variable_value='admin:admin;radmin:radmin' " +
                "WHERE variable_name='admin-admin_credentials';" +
                "UPDATE global_variables SET variable_value='0.0.0.0:6032' " +
                "WHERE variable_name='admin-mysql_ifaces';" +
                "LOAD ADMIN VARIABLES TO RUNTIME;" +
                "SAVE ADMIN VARIABLES TO DISK;";

        dockerService.execInContainer(containerId,
                "mysql", "-h127.0.0.1", "-P6032", "-uadmin", "-padmin", "-N", "-e", sql);

        log.info("ProxySQL remote admin access configured successfully");
    }

    /**
     * Configure ProxySQL for a new cluster.
     * Sets up MySQL servers, users, and query routing rules.
     * 
     * @param cluster The cluster to configure
     */
    public void configureCluster(Cluster cluster) {
        log.info("Configuring ProxySQL for cluster: {}", cluster.getId());

        // First, ensure remote admin access is configured
        setupRemoteAdminAccess(cluster);

        String masterContainer = "mysql-" + cluster.getId() + "-master";

        // Get MySQL IPs for ProxySQL routing (inside Docker network)
        String masterIp = dockerService.getContainerIpByName(masterContainer);
        if (masterIp == null) {
            throw new RuntimeException("Cannot get MySQL master container IP for: " + masterContainer);
        }

        // Connect to ProxySQL via published port on localhost
        int adminPort = getPublishedAdminPort(cluster.getId());
        log.info("Connecting to ProxySQL at localhost:{}, Master IP: {}", adminPort, masterIp);

        try (Connection conn = getAdminConnection(adminPort)) {
            Statement stmt = conn.createStatement();

            // Add MySQL user
            stmt.executeUpdate(String.format(
                    "INSERT INTO mysql_users (username, password, default_hostgroup) " +
                            "VALUES ('app_user', 'app_password', %d)",
                    WRITE_HOSTGROUP));

            // Add master to write hostgroup
            stmt.executeUpdate(String.format(
                    "INSERT INTO mysql_servers (hostgroup_id, hostname, port, weight) " +
                            "VALUES (%d, '%s', 3306, 1000)",
                    WRITE_HOSTGROUP, masterIp));

            // Add replicas to read hostgroup
            int replicaIndex = 1;
            for (String replicaId : cluster.getReplicaContainerIds()) {
                String replicaContainer = "mysql-" + cluster.getId() + "-replica-" + replicaIndex;
                String replicaIp = dockerService.getContainerIpByName(replicaContainer);
                if (replicaIp != null) {
                    stmt.executeUpdate(String.format(
                            "INSERT INTO mysql_servers (hostgroup_id, hostname, port, weight) " +
                                    "VALUES (%d, '%s', 3306, 500)",
                            READ_HOSTGROUP, replicaIp));
                }
                replicaIndex++;
            }

            // Add query rules for read/write splitting
            stmt.executeUpdate(String.format(
                    "INSERT INTO mysql_query_rules (rule_id, match_pattern, destination_hostgroup, apply) " +
                            "VALUES (1, '^SELECT.*FOR UPDATE', %d, 1)",
                    WRITE_HOSTGROUP));

            stmt.executeUpdate(String.format(
                    "INSERT INTO mysql_query_rules (rule_id, match_pattern, destination_hostgroup, apply) " +
                            "VALUES (2, '^SELECT', %d, 1)",
                    READ_HOSTGROUP));

            // Load configuration to runtime
            stmt.executeUpdate("LOAD MYSQL USERS TO RUNTIME");
            stmt.executeUpdate("LOAD MYSQL SERVERS TO RUNTIME");
            stmt.executeUpdate("LOAD MYSQL QUERY RULES TO RUNTIME");

            // Save to disk
            stmt.executeUpdate("SAVE MYSQL USERS TO DISK");
            stmt.executeUpdate("SAVE MYSQL SERVERS TO DISK");
            stmt.executeUpdate("SAVE MYSQL QUERY RULES TO DISK");

            log.info("ProxySQL configured successfully for cluster: {}", cluster.getId());

        } catch (Exception e) {
            log.error("Failed to configure ProxySQL for cluster: {}", cluster.getId(), e);
            throw new RuntimeException("ProxySQL configuration failed", e);
        }
    }

    /**
     * Add a new replica to ProxySQL.
     */
    public void addReplica(Cluster cluster, String replicaContainerId) {
        int replicaIndex = cluster.getReplicaContainerIds().size();
        String replicaContainer = "mysql-" + cluster.getId() + "-replica-" + replicaIndex;
        String replicaIp = dockerService.getContainerIpByName(replicaContainer);

        if (replicaIp == null) {
            log.error("Cannot get replica IP for addReplica");
            return;
        }

        int adminPort = getPublishedAdminPort(cluster.getId());

        try (Connection conn = getAdminConnection(adminPort)) {
            Statement stmt = conn.createStatement();

            stmt.executeUpdate(String.format(
                    "INSERT INTO mysql_servers (hostgroup_id, hostname, port, weight) " +
                            "VALUES (%d, '%s', 3306, 500)",
                    READ_HOSTGROUP, replicaIp));

            stmt.executeUpdate("LOAD MYSQL SERVERS TO RUNTIME");
            stmt.executeUpdate("SAVE MYSQL SERVERS TO DISK");

            log.info("Added replica {} to ProxySQL", replicaIp);

        } catch (Exception e) {
            log.error("Failed to add replica to ProxySQL", e);
        }
    }

    /**
     * Remove a server from ProxySQL.
     */
    public void removeServer(String hostname) {
        log.info("Removing server from ProxySQL: {}", hostname);
    }

    /**
     * Update master server after failover.
     */
    public void updateMaster(Cluster cluster, String newMasterContainer) {
        String oldMasterContainer = "mysql-" + cluster.getId() + "-master";

        String oldMasterIp = dockerService.getContainerIpByName(oldMasterContainer);
        String newMasterIp = dockerService.getContainerIpByName(newMasterContainer);
        int adminPort = getPublishedAdminPort(cluster.getId());

        try (Connection conn = getAdminConnection(adminPort)) {
            Statement stmt = conn.createStatement();

            if (oldMasterIp != null) {
                stmt.executeUpdate(String.format(
                        "UPDATE mysql_servers SET hostgroup_id = %d WHERE hostname = '%s'",
                        READ_HOSTGROUP, oldMasterIp));
            }

            if (newMasterIp != null) {
                stmt.executeUpdate(String.format(
                        "UPDATE mysql_servers SET hostgroup_id = %d WHERE hostname = '%s'",
                        WRITE_HOSTGROUP, newMasterIp));
            }

            stmt.executeUpdate("LOAD MYSQL SERVERS TO RUNTIME");
            stmt.executeUpdate("SAVE MYSQL SERVERS TO DISK");

            log.info("Updated ProxySQL master to: {}", newMasterIp);

        } catch (Exception e) {
            log.error("Failed to update ProxySQL master", e);
        }
    }

    /**
     * Get server statistics from ProxySQL.
     */
    public String getServerStats(String clusterId) {
        return "{}";
    }

    /**
     * Get admin connection via localhost published port.
     * Uses MariaDB driver for better ProxySQL compatibility.
     */
    private Connection getAdminConnection(int port) throws Exception {
        // Use MariaDB driver - more compatible with ProxySQL admin interface
        // MariaDB driver doesn't send problematic initialization queries
        String url = String.format("jdbc:mariadb://localhost:%d/", port);

        log.info("Connecting to ProxySQL admin at: localhost:{} with user: radmin (MariaDB driver)", port);

        Properties props = new Properties();
        props.setProperty("user", "radmin");
        props.setProperty("password", "radmin");
        props.setProperty("connectTimeout", "10000");
        props.setProperty("socketTimeout", "10000");

        return DriverManager.getConnection(url, props);
    }
}
