-- V4: Create tasks table for async operation tracking
CREATE TABLE tasks (
    id VARCHAR(36) PRIMARY KEY,
    cluster_id VARCHAR(36),
    type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    log_output TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tasks_cluster FOREIGN KEY (cluster_id) REFERENCES clusters(id) ON DELETE SET NULL
);

-- Indexes
CREATE INDEX idx_tasks_cluster ON tasks(cluster_id);
CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_tasks_type ON tasks(type);
