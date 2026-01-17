package com.dbaas.model;

import jakarta.persistence.*;
import lombok.*;

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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "cluster_replicas", joinColumns = @JoinColumn(name = "cluster_id"))
    @Column(name = "container_id")
    @Builder.Default
    private List<String> replicaContainerIds = new ArrayList<>();

    private String errorMessage;

    // Description
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "enable_orchestrator")
    @Builder.Default
    private boolean enableOrchestrator = true;

    @Column(name = "enable_backup")
    @Builder.Default
    private boolean enableBackup = false;

    @Column(nullable = false)
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
