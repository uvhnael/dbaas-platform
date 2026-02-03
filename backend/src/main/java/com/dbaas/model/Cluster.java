package com.dbaas.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a MySQL HA Cluster.
 */
@Entity
@Table(name = "clusters")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cluster {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "owner_id", nullable = false)
    private String userId;

    @Column(name = "db_version", nullable = false)
    private String mysqlVersion;

    @Column(nullable = false)
    private int replicaCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ClusterStatus status = ClusterStatus.PROVISIONING;

    private String networkId;

    private String masterContainerId;

    @Column(name = "proxysql_container_id")
    private String proxySqlContainerId;

    /**
     * Thay thế @ElementCollection bằng @JdbcTypeCode(SqlTypes.JSON)
     * Dữ liệu sẽ được lưu vào 1 cột 'replica_container_ids' duy nhất.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "replica_container_ids", columnDefinition = "json")
    @Builder.Default
    private List<String> replicaContainerIds = new ArrayList<>();

    private String errorMessage;

    // Connection configuration
    @Column(name = "db_user")
    @Builder.Default
    private String dbUser = "app_user";

    @Column(name = "db_password")
    private String dbPassword; // Encrypted password

    @Column(name = "root_password")
    private String rootPassword; // Encrypted root password for admin

    @Column(name = "proxy_port")
    private Integer proxyPort; // Published port of ProxySQL (auto-assigned or configured)

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "enable_orchestrator")
    @Builder.Default
    private boolean enableOrchestrator = true;

    @Column(name = "enable_backup")
    @Builder.Default
    private boolean enableBackup = false;

    /**
     * Version field for optimistic locking.
     * Prevents lost updates when concurrent modifications occur.
     */
    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}