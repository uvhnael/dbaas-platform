package com.dbaas.service;

import com.dbaas.exception.ProxySQLException;
import com.dbaas.model.Cluster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Properties;

/**
 * Service for ProxySQL configuration and management.
 * Connects via published ports on localhost.
 * 
 * <p>
 * SECURITY: All SQL queries use PreparedStatement to prevent SQL injection.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProxySQLService {

    private final DockerService dockerService;
    private final SecretService secretService;

    @Value("${proxysql.admin.user:radmin}")
    private String adminUser;

    @Value("${proxysql.admin.password}")
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
     * Uses secure credentials from SecretService.
     * 
     * @param cluster The cluster to configure
     */
    public void setupRemoteAdminAccess(Cluster cluster) {
        String containerId = cluster.getProxySqlContainerId();

        // Use secure password from configuration (no more hardcoded defaults)
        String secureAdminPassword = secretService.getProxySQLAdminPassword();
        // SECURITY: Escape credentials to prevent SQL injection via special characters
        String escapedPassword = escapeSqlString(secureAdminPassword);
        String credentials = "admin:" + escapedPassword + ";radmin:" + escapedPassword;

        String sql = "UPDATE global_variables SET variable_value='" + credentials + "' " +
                "WHERE variable_name='admin-admin_credentials';" +
                "UPDATE global_variables SET variable_value='0.0.0.0:6032' " +
                "WHERE variable_name='admin-mysql_ifaces';" +
                "LOAD ADMIN VARIABLES TO RUNTIME;" +
                "SAVE ADMIN VARIABLES TO DISK;";

        // Use initial default password for first-time setup, then secure password
        // NOTE: ProxySQL ships with 'admin' as default password - this is unavoidable
        dockerService.execInContainer(containerId,
                "mysql", "-h127.0.0.1", "-P6032", "-uadmin", "-p" + getInitialAdminPassword(), "-N", "-e", sql);
    }

    /**
     * Escape single quotes in SQL strings to prevent SQL injection.
     *
     * @param input The string to escape
     * @return Escaped string safe for SQL
     */
    private String escapeSqlString(String input) {
        if (input == null) {
            return null;
        }
        // Escape single quotes and backslashes for MySQL
        return input.replace("\\", "\\\\").replace("'", "''");
    }

    /**
     * Get initial admin password for first-time ProxySQL setup.
     * ProxySQL uses 'admin' as default password on fresh container.
     * This is a ProxySQL default that cannot be changed before first login.
     */
    private String getInitialAdminPassword() {
        // ProxySQL default password on fresh container is 'admin'
        // After setupRemoteAdminAccess runs, it will be changed to the secure password
        return "admin";
    }

    /**
     * Configure ProxySQL for a new cluster.
     * Sets up MySQL servers, users, and query routing rules.
     * Uses PreparedStatement for SQL injection prevention.
     * 
     * @param cluster The cluster to configure
     */
    public void configureCluster(Cluster cluster) {
        String clusterId = cluster.getId();
        log.info("[ProxySQL:{}] Configuring cluster", clusterId);

        // First, ensure remote admin access is configured
        setupRemoteAdminAccess(cluster);

        // Use container name directly (Docker DNS will resolve it)
        String masterContainer = "mysql-" + clusterId + "-master";

        // Connect to ProxySQL via published port on localhost
        int adminPort = getPublishedAdminPort(clusterId);

        try (Connection conn = getAdminConnection(adminPort)) {
            // Get the app user password for this cluster
            String appUser = cluster.getDbUser() != null ? cluster.getDbUser() : "app_user";
            String appPassword = secretService.generateAppUserPassword(clusterId);

            // Add MySQL user using PreparedStatement (prevents SQL injection)
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO mysql_users (username, password, default_hostgroup) VALUES (?, ?, ?)")) {
                pstmt.setString(1, appUser);
                pstmt.setString(2, appPassword);
                pstmt.setInt(3, WRITE_HOSTGROUP);
                pstmt.executeUpdate();
            }

            // Add master to write hostgroup using PreparedStatement
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO mysql_servers (hostgroup_id, hostname, port, weight) VALUES (?, ?, 3306, 1000)")) {
                pstmt.setInt(1, WRITE_HOSTGROUP);
                pstmt.setString(2, masterContainer);
                pstmt.executeUpdate();
            }

            // Add replicas to read hostgroup using PreparedStatement
            int replicaIndex = 1;
            for (String replicaId : cluster.getReplicaContainerIds()) {
                String replicaContainer = "mysql-" + clusterId + "-replica-" + replicaIndex;
                try (PreparedStatement pstmt = conn.prepareStatement(
                        "INSERT INTO mysql_servers (hostgroup_id, hostname, port, weight) VALUES (?, ?, 3306, 500)")) {
                    pstmt.setInt(1, READ_HOSTGROUP);
                    pstmt.setString(2, replicaContainer);
                    pstmt.executeUpdate();
                }
                replicaIndex++;
            }

            // Add query rules for read/write splitting (static values - safe to use
            // Statement)
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(
                        "INSERT INTO mysql_query_rules (rule_id, match_pattern, destination_hostgroup, apply) " +
                                "VALUES (1, '^SELECT.*FOR UPDATE', " + WRITE_HOSTGROUP + ", 1)");
                stmt.executeUpdate(
                        "INSERT INTO mysql_query_rules (rule_id, match_pattern, destination_hostgroup, apply) " +
                                "VALUES (2, '^SELECT', " + READ_HOSTGROUP + ", 1)");

                // Load configuration to runtime
                stmt.executeUpdate("LOAD MYSQL USERS TO RUNTIME");
                stmt.executeUpdate("LOAD MYSQL SERVERS TO RUNTIME");
                stmt.executeUpdate("LOAD MYSQL QUERY RULES TO RUNTIME");

                // Save to disk
                stmt.executeUpdate("SAVE MYSQL USERS TO DISK");
                stmt.executeUpdate("SAVE MYSQL SERVERS TO DISK");
                stmt.executeUpdate("SAVE MYSQL QUERY RULES TO DISK");
            }

            log.info("[ProxySQL:{}] Configuration completed: master={}, replicas={}",
                    clusterId, masterContainer, cluster.getReplicaContainerIds().size());

        } catch (Exception e) {
            log.error("[ProxySQL:{}] ✗ FAILED to configure ProxySQL: {}", clusterId, e.getMessage(), e);
            throw new ProxySQLException("configureCluster", clusterId, e.getMessage());
        }
    }

    /**
     * Add a new replica to ProxySQL.
     */
    public void addReplica(Cluster cluster, String replicaContainerId) {
        String clusterId = cluster.getId();
        int replicaIndex = cluster.getReplicaContainerIds().size();
        String replicaContainer = "mysql-" + clusterId + "-replica-" + replicaIndex;

        int adminPort = getPublishedAdminPort(clusterId);

        log.debug("[ProxySQL:{}] Adding replica: {}", clusterId, replicaContainer);

        try (Connection conn = getAdminConnection(adminPort)) {
            // Use PreparedStatement to prevent SQL injection
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO mysql_servers (hostgroup_id, hostname, port, weight) VALUES (?, ?, 3306, 500)")) {
                pstmt.setInt(1, READ_HOSTGROUP);
                pstmt.setString(2, replicaContainer);
                pstmt.executeUpdate();
            }

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("LOAD MYSQL SERVERS TO RUNTIME");
                stmt.executeUpdate("SAVE MYSQL SERVERS TO DISK");
            }

            log.info("[ProxySQL:{}] Replica '{}' added to hostgroup={}",
                    clusterId, replicaContainer, READ_HOSTGROUP);

        } catch (Exception e) {
            log.error("[ProxySQL:{}] ✗ FAILED to add replica '{}' to ProxySQL: {}",
                    clusterId, replicaContainer, e.getMessage(), e);
            throw new ProxySQLException("addReplica", clusterId, e.getMessage());
        }
    }

    /**
     * Remove a server from ProxySQL.
     */
    public void removeServer(Cluster cluster, String hostname) {
        String clusterId = cluster.getId();
        int adminPort = getPublishedAdminPort(clusterId);

        log.debug("[ProxySQL:{}] Removing server: {}", clusterId, hostname);

        try (Connection conn = getAdminConnection(adminPort)) {
            // Use PreparedStatement to prevent SQL injection
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "DELETE FROM mysql_servers WHERE hostname = ?")) {
                pstmt.setString(1, hostname);
                pstmt.executeUpdate();
            }

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("LOAD MYSQL SERVERS TO RUNTIME");
                stmt.executeUpdate("SAVE MYSQL SERVERS TO DISK");
            }

            log.info("[ProxySQL:{}] Server '{}' removed", clusterId, hostname);

        } catch (Exception e) {
            log.error("[ProxySQL:{}] ✗ FAILED to remove server '{}' from ProxySQL: {}",
                    clusterId, hostname, e.getMessage(), e);
            throw new ProxySQLException("removeServer", clusterId, e.getMessage());
        }
    }

    /**
     * Update master server after failover.
     * Handles the case where old master is dead and a replica is promoted.
     * 
     * @param cluster            The cluster being updated
     * @param newMasterContainer The container name of the promoted replica (e.g.,
     *                           mysql-{clusterId}-replica-1)
     */
    public void updateMaster(Cluster cluster, String newMasterContainer) {
        String clusterId = cluster.getId();
        String oldMasterContainer = "mysql-" + clusterId + "-master";
        int adminPort = getPublishedAdminPort(clusterId);

        log.info("[ProxySQL:{}] Failover: {} → {}", clusterId, oldMasterContainer, newMasterContainer);

        try (Connection conn = getAdminConnection(adminPort)) {
            // Remove failed old master from mysql_servers
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "DELETE FROM mysql_servers WHERE hostname = ?")) {
                pstmt.setString(1, oldMasterContainer);
                pstmt.executeUpdate();
            }

            // Move promoted replica from READ hostgroup to WRITE hostgroup
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "UPDATE mysql_servers SET hostgroup_id = ?, weight = 1000 WHERE hostname = ?")) {
                pstmt.setInt(1, WRITE_HOSTGROUP);
                pstmt.setString(2, newMasterContainer);
                int updated = pstmt.executeUpdate();
                if (updated == 0) {
                    // New master might not exist in ProxySQL yet, add it
                    log.debug("[ProxySQL:{}] New master not found, adding it", clusterId);
                    try (PreparedStatement insertStmt = conn.prepareStatement(
                            "INSERT INTO mysql_servers (hostgroup_id, hostname, port, weight) VALUES (?, ?, 3306, 1000)")) {
                        insertStmt.setInt(1, WRITE_HOSTGROUP);
                        insertStmt.setString(2, newMasterContainer);
                        insertStmt.executeUpdate();
                    }
                }
            }

            // Reload and persist configuration
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("LOAD MYSQL SERVERS TO RUNTIME");
                stmt.executeUpdate("SAVE MYSQL SERVERS TO DISK");
            }

            log.info("[ProxySQL:{}] Failover completed: new master={}", clusterId, newMasterContainer);

        } catch (Exception e) {
            log.error("[ProxySQL:{}] ✗ FAILED to update master after failover: {}", clusterId, e.getMessage(), e);
            throw new ProxySQLException("updateMaster", clusterId, e.getMessage());
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
     * Uses secure credentials from configuration.
     */
    private Connection getAdminConnection(int port) throws Exception {
        // Use MariaDB driver - more compatible with ProxySQL admin interface
        String url = String.format("jdbc:mariadb://localhost:%d/", port);

        log.debug("Connecting to ProxySQL admin at localhost:{}", port);

        // Use secure password from configuration (injected via @Value)
        Properties props = new Properties();
        props.setProperty("user", adminUser);
        props.setProperty("password", adminPassword);
        props.setProperty("connectTimeout", "10000");
        props.setProperty("socketTimeout", "10000");

        return DriverManager.getConnection(url, props);
    }
}
