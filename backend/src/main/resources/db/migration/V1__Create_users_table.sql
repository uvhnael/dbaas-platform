-- V1: Create users table
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100),
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Create index for username lookup
CREATE INDEX idx_users_username ON users(username);

-- Insert default admin user (password: admin123)
INSERT INTO users (id, username, password_hash, role, enabled, created_at) 
VALUES (
    'admin-00000000-0000-0000-0000-000000000000',
    'admin',
    '$2a$10$N9qo8uLOickgx2ZMRZoMy.Mrz4LNl9X9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z9Z',
    'ADMIN',
    TRUE,
    CURRENT_TIMESTAMP
);
