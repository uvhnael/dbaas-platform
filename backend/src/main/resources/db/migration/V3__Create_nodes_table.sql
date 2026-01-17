-- V3: Create nodes table
CREATE TABLE nodes (
    id VARCHAR(36) PRIMARY KEY,
    cluster_id VARCHAR(36) NOT NULL,
    container_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    ip_address VARCHAR(45),
    port INT NOT NULL DEFAULT 3306,
    status VARCHAR(30) NOT NULL DEFAULT 'STARTING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_nodes_cluster FOREIGN KEY (cluster_id) REFERENCES clusters(id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_nodes_cluster ON nodes(cluster_id);
CREATE INDEX idx_nodes_role ON nodes(role);
CREATE INDEX idx_nodes_status ON nodes(status);
