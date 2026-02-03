package com.dbaas.util;

/**
 * Utility class for hostname parsing and manipulation.
 * 
 * <p>
 * Provides common helper methods for working with Docker container hostnames
 * in the DBaaS platform. Hostnames follow the pattern:
 * mysql-{clusterId}-{role}[:port]
 * </p>
 * 
 * <p>
 * Examples:
 * <ul>
 * <li>mysql-abc123-master</li>
 * <li>mysql-abc123-master:3306</li>
 * <li>mysql-abc123-replica-1</li>
 * <li>proxysql-abc123</li>
 * </ul>
 * </p>
 */
public final class HostnameUtils {

    private HostnameUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Remove port from hostname if present.
     * E.g., "mysql-abc123-master:3306" → "mysql-abc123-master"
     * 
     * @param hostname Hostname with optional port
     * @return Clean hostname without port, or null if input is null
     */
    public static String removePort(String hostname) {
        if (hostname == null)
            return null;
        return hostname.split(":")[0];
    }

    /**
     * Extract cluster ID from container hostname.
     * Handles hostnames like "mysql-abc123-master" or "mysql-abc123-replica-1".
     * 
     * @param hostname Container hostname (with or without port)
     * @return Cluster ID or null if cannot extract
     */
    public static String extractClusterId(String hostname) {
        String host = removePort(hostname);
        if (host == null)
            return null;

        if (host.startsWith("mysql-") || host.startsWith("proxysql-")) {
            String[] parts = host.split("-");
            if (parts.length >= 2) {
                return parts[1];
            }
        }
        return null;
    }

    /**
     * Extract role from container hostname.
     * E.g., "mysql-abc123-master" → "master"
     * "mysql-abc123-replica-1" → "replica-1"
     * 
     * @param hostname Container hostname (with or without port)
     * @return Role or null if cannot extract
     */
    public static String extractRole(String hostname) {
        String host = removePort(hostname);
        if (host == null)
            return null;

        if (host.startsWith("mysql-")) {
            String[] parts = host.split("-");
            if (parts.length >= 3) {
                // Role is everything after mysql-{clusterId}-
                StringBuilder role = new StringBuilder(parts[2]);
                for (int i = 3; i < parts.length; i++) {
                    role.append("-").append(parts[i]);
                }
                return role.toString();
            }
        }
        return null;
    }

    /**
     * Build container name from cluster ID and role.
     * 
     * @param clusterId The cluster ID
     * @param role      The role (master, replica-1, etc.)
     * @return Container name like "mysql-abc123-master"
     */
    public static String buildContainerName(String clusterId, String role) {
        return String.format("mysql-%s-%s", clusterId, role);
    }

    /**
     * Check if hostname is a master container.
     * 
     * @param hostname Container hostname
     * @return true if this is a master container
     */
    public static boolean isMaster(String hostname) {
        String role = extractRole(hostname);
        return "master".equals(role);
    }

    /**
     * Check if hostname is a replica container.
     * 
     * @param hostname Container hostname
     * @return true if this is a replica container
     */
    public static boolean isReplica(String hostname) {
        String role = extractRole(hostname);
        return role != null && role.startsWith("replica");
    }

    /**
     * Extract replica number from hostname.
     * E.g., "mysql-abc123-replica-2" → 2
     * 
     * @param hostname Container hostname
     * @return Replica number or -1 if not a replica
     */
    public static int extractReplicaNumber(String hostname) {
        String role = extractRole(hostname);
        if (role == null || !role.startsWith("replica-")) {
            return -1;
        }
        try {
            return Integer.parseInt(role.replace("replica-", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
