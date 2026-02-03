package com.dbaas.service;

import com.dbaas.config.SecurityProperties;
import com.dbaas.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for managing cluster-related secrets.
 * Generates secure, unique passwords for each cluster.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecretService {

    private final SecurityProperties securityProperties;
    private final EncryptionUtil encryptionUtil;

    /**
     * Generate MySQL root password for a cluster.
     * Password is unique per cluster for isolation.
     *
     * @param clusterId The cluster identifier
     * @return Generated password
     */
    public String generateMySQLRootPassword(String clusterId) {
        String basePassword = securityProperties.getCluster().getMysqlRootPassword();
        return encryptionUtil.hashClusterPassword(clusterId, basePassword);
    }

    /**
     * Get replication user password.
     * Returns FIXED password because Orchestrator needs to connect
     * to replicas with the same credentials across all clusters.
     *
     * @param clusterId The cluster identifier (ignored - kept for API
     *                  compatibility)
     * @return Fixed replication password from configuration
     */
    public String generateReplicationPassword(String clusterId) {
        // Orchestrator uses repl user, so must be fixed across all clusters
        return securityProperties.getCluster().getReplicationPassword();
    }

    /**
     * Get orchestrator user password.
     * Returns FIXED password (not per-cluster hashed) because Orchestrator
     * is a shared service that connects to all clusters with the same credentials.
     *
     * @param clusterId The cluster identifier (ignored - kept for API
     *                  compatibility)
     * @return Fixed orchestrator password from configuration
     */
    public String generateOrchestratorPassword(String clusterId) {
        // Orchestrator is shared across clusters, so use fixed password
        return securityProperties.getCluster().getOrchestratorPassword();
    }

    /**
     * Get ProxySQL admin password.
     * This is shared across clusters (admin interface is per-container).
     *
     * @return ProxySQL admin password
     */
    public String getProxySQLAdminPassword() {
        return securityProperties.getCluster().getProxysqlAdminPassword();
    }

    /**
     * Generate a monitoring user password for a cluster.
     *
     * @param clusterId The cluster identifier
     * @return Generated password
     */
    public String generateMonitoringPassword(String clusterId) {
        String basePassword = securityProperties.getCluster().getMysqlRootPassword();
        return encryptionUtil.hashClusterPassword(clusterId, basePassword + ":monitor");
    }

    /**
     * Generate app user password for a cluster.
     * This password is used by applications connecting via ProxySQL.
     *
     * @param clusterId The cluster identifier
     * @return Generated password
     */
    public String generateAppUserPassword(String clusterId) {
        String basePassword = securityProperties.getCluster().getMysqlRootPassword();
        return encryptionUtil.hashClusterPassword(clusterId, basePassword + ":app");
    }

    /**
     * Encrypt sensitive data for storage.
     *
     * @param plaintext Data to encrypt
     * @return Encrypted data
     */
    public String encryptSecret(String plaintext) {
        String key = securityProperties.getEncryption().getKey();
        if (key == null || key.isEmpty()) {
            throw new IllegalStateException(
                    "ENCRYPTION_KEY is required but not configured. " +
                            "Set the ENCRYPTION_KEY environment variable with a Base64-encoded 256-bit key.");
        }
        return encryptionUtil.encrypt(plaintext, key);
    }

    /**
     * Decrypt stored sensitive data.
     *
     * @param ciphertext Encrypted data
     * @return Decrypted data
     */
    public String decryptSecret(String ciphertext) {
        String key = securityProperties.getEncryption().getKey();
        if (key == null || key.isEmpty()) {
            throw new IllegalStateException(
                    "ENCRYPTION_KEY is required but not configured. " +
                            "Set the ENCRYPTION_KEY environment variable with a Base64-encoded 256-bit key.");
        }
        return encryptionUtil.decrypt(ciphertext, key);
    }
}
