package com.dbaas.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Externalized security properties.
 * Secrets should be provided via environment variables or secret management system.
 */
@Configuration
@ConfigurationProperties(prefix = "security")
@Validated
@Getter
@Setter
public class SecurityProperties {

    /**
     * JWT configuration
     */
    private Jwt jwt = new Jwt();

    /**
     * Database password encryption
     */
    private Encryption encryption = new Encryption();

    /**
     * MySQL cluster passwords
     */
    private ClusterSecrets cluster = new ClusterSecrets();

    @Getter
    @Setter
    public static class Jwt {
        /**
         * JWT signing secret key.
         * MUST be set via environment variable: SECURITY_JWT_SECRET
         */
        @NotBlank(message = "JWT secret must be configured")
        private String secret;

        /**
         * JWT expiration time in milliseconds.
         * Default: 24 hours
         */
        private long expirationMs = 86400000;

        /**
         * JWT refresh token expiration in milliseconds.
         * Default: 7 days
         */
        private long refreshExpirationMs = 604800000;

        /**
         * JWT issuer
         */
        private String issuer = "dbaas-platform";
    }

    @Getter
    @Setter
    public static class Encryption {
        /**
         * AES encryption key for sensitive data.
         * MUST be set via environment variable: SECURITY_ENCRYPTION_KEY
         * Should be 32 bytes (256-bit) base64 encoded
         */
        private String key;

        /**
         * Algorithm to use (AES-256-GCM recommended)
         */
        private String algorithm = "AES/GCM/NoPadding";
    }

    @Getter
    @Setter
    public static class ClusterSecrets {
        /**
         * Base password for MySQL root users.
         * MUST be set via environment variable: SECURITY_CLUSTER_MYSQL_ROOT_PASSWORD
         */
        private String mysqlRootPassword = "changeme";

        /**
         * Password for replication user.
         * MUST be set via environment variable: SECURITY_CLUSTER_REPLICATION_PASSWORD
         */
        private String replicationPassword = "repl_changeme";

        /**
         * Password for orchestrator user.
         * MUST be set via environment variable: SECURITY_CLUSTER_ORCHESTRATOR_PASSWORD
         */
        private String orchestratorPassword = "orch_changeme";

        /**
         * Password for ProxySQL admin.
         * MUST be set via environment variable: SECURITY_CLUSTER_PROXYSQL_ADMIN_PASSWORD
         */
        private String proxysqlAdminPassword = "admin";
    }
}
