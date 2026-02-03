-- ═══════════════════════════════════════════════════════════════════════════════
-- DBaaS Platform - Initial Schema
-- Created: 2026-01-23
-- ═══════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────────
-- USERS TABLE
-- ─────────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id              VARCHAR(36) PRIMARY KEY,
    username        VARCHAR(100) NOT NULL UNIQUE,
    email           VARCHAR(255),
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(20) NOT NULL DEFAULT 'USER',
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);

-- ─────────────────────────────────────────────────────────────────────────────────
-- CLUSTERS TABLE
-- ─────────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS clusters (
    id                      VARCHAR(36) PRIMARY KEY,
    name                    VARCHAR(100) NOT NULL,
    owner_id                VARCHAR(36) NOT NULL,
    db_version              VARCHAR(20) NOT NULL DEFAULT '8.0',
    replica_count           INT NOT NULL DEFAULT 2,
    status                  VARCHAR(30) NOT NULL DEFAULT 'PROVISIONING',
    network_id              VARCHAR(100),
    master_container_id     VARCHAR(100),
    proxysql_container_id   VARCHAR(100),
    replica_container_ids   JSON,
    error_message           TEXT,
    db_user                 VARCHAR(50) DEFAULT 'app_user',
    db_password             VARCHAR(255),
    root_password           VARCHAR(255),
    proxy_port              INT,
    description             TEXT,
    enable_orchestrator     BOOLEAN NOT NULL DEFAULT TRUE,
    enable_backup           BOOLEAN NOT NULL DEFAULT FALSE,
    version                 BIGINT DEFAULT 0,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP,
    
    CONSTRAINT fk_clusters_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_clusters_owner ON clusters(owner_id);
CREATE INDEX idx_clusters_status ON clusters(status);
CREATE INDEX idx_clusters_name ON clusters(name);

-- ─────────────────────────────────────────────────────────────────────────────────
-- NODES TABLE
-- ─────────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS nodes (
    id              VARCHAR(36) PRIMARY KEY,
    cluster_id      VARCHAR(36) NOT NULL,
    container_name  VARCHAR(100) NOT NULL,
    role            VARCHAR(20) NOT NULL,
    ip_address      VARCHAR(45),
    port            INT NOT NULL DEFAULT 3306,
    status          VARCHAR(20) NOT NULL DEFAULT 'STARTING',
    container_id    VARCHAR(100),
    cpu_cores       INT NOT NULL DEFAULT 2,
    memory          VARCHAR(20) NOT NULL DEFAULT '4G',
    storage         VARCHAR(20) NOT NULL DEFAULT '10G',
    is_read_only    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    
    CONSTRAINT fk_nodes_cluster FOREIGN KEY (cluster_id) REFERENCES clusters(id) ON DELETE CASCADE
);

CREATE INDEX idx_nodes_cluster ON nodes(cluster_id);
CREATE INDEX idx_nodes_container_name ON nodes(container_name);
CREATE INDEX idx_nodes_status ON nodes(status);

-- ─────────────────────────────────────────────────────────────────────────────────
-- TASKS TABLE
-- ─────────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS tasks (
    id              VARCHAR(36) PRIMARY KEY,
    cluster_id      VARCHAR(36) NOT NULL,
    type            VARCHAR(30) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    log_output      TEXT,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_tasks_cluster FOREIGN KEY (cluster_id) REFERENCES clusters(id) ON DELETE CASCADE
);

CREATE INDEX idx_tasks_cluster ON tasks(cluster_id);
CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_tasks_created ON tasks(created_at);

-- ─────────────────────────────────────────────────────────────────────────────────
-- BACKUPS TABLE
-- ─────────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS backups (
    id              VARCHAR(36) PRIMARY KEY,
    cluster_id      VARCHAR(36) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    size_bytes      BIGINT,
    storage_path    VARCHAR(500),
    backup_type     VARCHAR(20) NOT NULL DEFAULT 'FULL',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMP,
    error_message   TEXT,
    
    CONSTRAINT fk_backups_cluster FOREIGN KEY (cluster_id) REFERENCES clusters(id) ON DELETE CASCADE
);

CREATE INDEX idx_backups_cluster ON backups(cluster_id);
CREATE INDEX idx_backups_status ON backups(status);
CREATE INDEX idx_backups_created ON backups(created_at DESC);
