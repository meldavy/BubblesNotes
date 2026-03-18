-- Users table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    given_name VARCHAR(100),
    family_name VARCHAR(100),
    picture_url TEXT,
    oauth_token BYTEA NOT NULL,
    encryption_salt CHAR(32) NOT NULL,
    api_key VARCHAR(64) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Tags table
CREATE TABLE IF NOT EXISTS tags (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, name)
);

-- Notes table
CREATE TABLE IF NOT EXISTS notes (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    title TEXT,
    content TEXT NOT NULL,
    is_published BOOLEAN DEFAULT TRUE,
    ai_title VARCHAR(255),
    ai_summary TEXT,
    ai_tags JSONB,
    last_version_id BIGINT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Version table
CREATE TABLE IF NOT EXISTS versions (
    id BIGSERIAL PRIMARY KEY,
    note_id BIGINT NOT NULL REFERENCES notes(id),
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    tags JSONB NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW()
);

-- NoteTag join table
CREATE TABLE IF NOT EXISTS note_tags (
    note_id BIGINT NOT NULL REFERENCES notes(id),
    tag_id BIGINT NOT NULL REFERENCES tags(id),
    PRIMARY KEY (note_id, tag_id)
);

-- Attachments table
CREATE TABLE IF NOT EXISTS attachments (
    id BIGSERIAL PRIMARY KEY,
    note_id BIGINT NOT NULL REFERENCES notes(id),
    user_id UUID NOT NULL REFERENCES users(id),
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_path TEXT NOT NULL,
    encrypted_data BYTEA,
    created_at TIMESTAMP DEFAULT NOW()
);

-- API Tokens table
CREATE TABLE IF NOT EXISTS api_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash CHAR(64) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

-- AI Tasks table
CREATE TABLE IF NOT EXISTS ai_tasks (
    id BIGSERIAL PRIMARY KEY,
    note_id BIGINT NOT NULL REFERENCES notes(id),
    status VARCHAR(20) DEFAULT 'pending',
    result JSONB,
    error_message TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP
);

-- Search vector column for full-text search
ALTER TABLE notes ADD COLUMN IF NOT EXISTS search_vector TSVECTOR;

-- GIN index for search vector
CREATE INDEX IF NOT EXISTS idx_notes_search ON notes USING GIN(search_vector);
