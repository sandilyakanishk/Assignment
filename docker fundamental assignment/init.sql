-- =============================================================
--  backend/init.sql  —  Database Initialization Script
--  Author : Kanishk Sandilya
--  Runs automatically when Postgres container starts for the
--  first time (mounted into /docker-entrypoint-initdb.d/)
-- =============================================================

CREATE TABLE IF NOT EXISTS users (
    id         SERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(150) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Seed some sample data
INSERT INTO users (name, email) VALUES
    ('Kanishk Sandilya', 'kanishk@example.com'),
    ('Alice Johnson',    'alice@example.com'),
    ('Bob Smith',        'bob@example.com')
ON CONFLICT (email) DO NOTHING;
