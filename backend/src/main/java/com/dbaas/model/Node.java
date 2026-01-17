package com.dbaas.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Entity representing a cluster node (MySQL instance).
 */
@Entity
@Table(name = "nodes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Node {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "cluster_id", nullable = false)
    private String clusterId;

    @Column(name = "container_name", nullable = false)
    private String containerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NodeRole role;

    @Column(name = "ip_address")
    private String ipAddress;

    private int port;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private NodeStatus status = NodeStatus.STARTING;

    @Column(name = "container_id")
    private String containerId;

    @Column(name = "cpu_cores")
    @Builder.Default
    private int cpuCores = 2;

    @Column(name = "memory")
    @Builder.Default
    private String memory = "4G";

    @Column(name = "is_read_only")
    @Builder.Default
    private boolean readOnly = false;

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
