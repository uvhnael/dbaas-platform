-- V2: Recreate clusters table with proper structure
CREATE TABLE clusters (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    owner_id VARCHAR(36) NOT NULL,
    db_version VARCHAR(20) NOT NULL DEFAULT '8.0',
    replica_count INT NOT NULL DEFAULT 2,
    status VARCHAR(30) NOT NULL DEFAULT 'PROVISIONING',
    network_id VARCHAR(100),
    master_container_id VARCHAR(100),
    proxysql_container_id VARCHAR(100),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_clusters_owner FOREIGN KEY (owner_id) REFERENCES users(id)
);

-- Indexes
CREATE INDEX idx_clusters_owner ON clusters(owner_id);
CREATE INDEX idx_clusters_status ON clusters(status);

-- Cluster replicas junction table
CREATE TABLE cluster_replicas (
    cluster_id VARCHAR(36) NOT NULL,
    container_id VARCHAR(100) NOT NULL,
    PRIMARY KEY (cluster_id, container_id),
    CONSTRAINT fk_cluster_replicas_cluster FOREIGN KEY (cluster_id) REFERENCES clusters(id) ON DELETE CASCADE
);
