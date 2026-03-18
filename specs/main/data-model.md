# Data Model: Notes App with AI Integration

**Branch**: `notes-app-ai-integration` | **Date**: 2026-03-18

## Entity Definitions

### User

Represents a user authenticated via Google OAuth.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | UUID | PRIMARY KEY, NOT NULL | Unique user identifier from Google OAuth |
| email | VARCHAR(255) | UNIQUE, NOT NULL | User's email address |
| name | VARCHAR(255) | NOT NULL | User's display name |
| given_name | VARCHAR(100) | | First name from OAuth profile |
| family_name | VARCHAR(100) | | Last name from OAuth profile |
| picture_url | TEXT | | Profile picture URL |
| oauth_token | BYTEA | NOT NULL | Encrypted OAuth token (AES-GCM) |
| encryption_salt | CHAR(32) | NOT NULL | Salt for key derivation (hex encoded) |
| api_key | VARCHAR(64) | UNIQUE, NOT NULL | Per-user API key (SHA-256 hash) |
| created_at | TIMESTAMP | DEFAULT NOW() | Account creation timestamp |
| updated_at | TIMESTAMP | DEFAULT NOW() | Last update timestamp |

**Validation Rules**:
- email must be valid RFC 5322 format
- api_key must be exactly 64 hex characters

**State Transitions**: N/A (user entity is immutable after creation)

---

### Note

Represents a user's note with content, tags, and metadata.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | BIGSERIAL | PRIMARY KEY, NOT NULL | Auto-incrementing ID |
| user_id | UUID | REFERENCES users(id), NOT NULL | Owner's user ID |
| title | TEXT | | Note title (auto-generated from content if empty) |
| content | TEXT | NOT NULL | Markdown content |
| is_published | BOOLEAN | DEFAULT TRUE | Draft vs published status |
| ai_title | VARCHAR(255) | | AI-generated title |
| ai_summary | TEXT | | AI-generated summary |
| ai_tags | JSONB | | AI-suggested tags as array of strings |
| last_version_id | BIGINT | REFERENCES versions(id) | Current version reference |
| created_at | TIMESTAMP | DEFAULT NOW() | Creation timestamp |
| updated_at | TIMESTAMP | DEFAULT NOW() | Last update timestamp |

**Validation Rules**:
- content cannot be empty
- if title is empty, use first line of content as title

**State Transitions**:
```
Draft (is_published = false) → Published (is_published = true)
  Trigger: Auto-save on content change or explicit publish
```

---

### Version

Tracks historical versions of notes for versioning system.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | BIGSERIAL | PRIMARY KEY, NOT NULL | Auto-incrementing ID |
| note_id | BIGINT | REFERENCES notes(id), NOT NULL | Parent note reference |
| title | TEXT | NOT NULL | Title at time of version |
| content | TEXT | NOT NULL | Content at time of version |
| tags | JSONB | NOT NULL | Tags as array of strings |
| created_by | UUID | REFERENCES users(id), NOT NULL | User who made the change |
| created_at | TIMESTAMP | DEFAULT NOW() | Version creation timestamp |

**Validation Rules**: None (audit trail, always valid)

**State Transitions**: N/A (append-only)

---

### Tag

Categorization entity for notes.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | BIGSERIAL | PRIMARY KEY, NOT NULL | Auto-incrementing ID |
| user_id | UUID | REFERENCES users(id), NOT NULL | Owner's user ID |
| name | VARCHAR(50) | NOT NULL | Tag name (normalized to lowercase) |
| created_at | TIMESTAMP | DEFAULT NOW() | Creation timestamp |

**Validation Rules**:
- name must be unique per user
- normalized to lowercase, trimmed whitespace

**State Transitions**: N/A

---

### NoteTag (Join Table)

Many-to-many relationship between notes and tags.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| note_id | BIGINT | REFERENCES notes(id), NOT NULL | Note reference |
| tag_id | BIGINT | REFERENCES tags(id), NOT NULL | Tag reference |

**Primary Key**: (note_id, tag_id)

**Validation Rules**:
- Both note_id and tag_id must exist
- Same tag cannot be linked to same note twice

---

### Attachment

File metadata entity for note attachments.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | BIGSERIAL | PRIMARY KEY, NOT NULL | Auto-incrementing ID |
| note_id | BIGINT | REFERENCES notes(id), NOT NULL | Parent note reference |
| user_id | UUID | REFERENCES users(id), NOT NULL | Owner's user ID (denormalized for efficiency) |
| filename | VARCHAR(255) | NOT NULL | Original filename |
| content_type | VARCHAR(100) | NOT NULL | MIME type |
| file_size | BIGINT | NOT NULL | File size in bytes |
| storage_path | TEXT | NOT NULL | Encrypted file storage path |
| encrypted_data | BYTEA | | Encrypted file data (if stored in DB) |
| created_at | TIMESTAMP | DEFAULT NOW() | Upload timestamp |

**Validation Rules**:
- file_size must be <= max_attachment_size (configurable via environment variable)
- content_type must be valid MIME type

**State Transitions**: N/A

---

### APIToken

Per-user API credentials for programmatic access.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | BIGSERIAL | PRIMARY KEY, NOT NULL | Auto-incrementing ID |
| user_id | UUID | REFERENCES users(id), NOT NULL | Owner's user ID |
| token_hash | CHAR(64) | UNIQUE, NOT NULL | SHA-256 hash of API token |
| name | VARCHAR(100) | NOT NULL | User-friendly name for the token |
| last_used_at | TIMESTAMP | | Last usage timestamp |
| created_at | TIMESTAMP | DEFAULT NOW() | Creation timestamp |

**Validation Rules**:
- token_hash must be exactly 64 hex characters
- Each user can have multiple API tokens

---

### AITask

Tracks AI processing tasks for notes.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | BIGSERIAL | PRIMARY KEY, NOT NULL | Auto-incrementing ID |
| note_id | BIGINT | REFERENCES notes(id), NOT NULL | Note being processed |
| status | VARCHAR(20) | DEFAULT 'pending' | pending, processing, completed, failed |
| result | JSONB | | AI-generated results (title, summary, tags) |
| error_message | TEXT | | Error if processing failed |
| started_at | TIMESTAMP | | Processing start time |
| completed_at | TIMESTAMP | | Processing completion time |

**Validation Rules**:
- status must be one of: pending, processing, completed, failed

**State Transitions**:
```
pending → processing → completed
         ↓
       failed (on error)
```

---

## Database Schema Summary

```sql
-- Users table
CREATE TABLE users (
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
CREATE TABLE tags (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, name)
);

-- Notes table
CREATE TABLE notes (
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
CREATE TABLE versions (
    id BIGSERIAL PRIMARY KEY,
    note_id BIGINT NOT NULL REFERENCES notes(id),
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    tags JSONB NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW()
);

-- NoteTag join table
CREATE TABLE note_tags (
    note_id BIGINT NOT NULL REFERENCES notes(id),
    tag_id BIGINT NOT NULL REFERENCES tags(id),
    PRIMARY KEY (note_id, tag_id)
);

-- Attachments table
CREATE TABLE attachments (
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
CREATE TABLE api_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash CHAR(64) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

-- AI Tasks table
CREATE TABLE ai_tasks (
    id BIGSERIAL PRIMARY KEY,
    note_id BIGINT NOT NULL REFERENCES notes(id),
    status VARCHAR(20) DEFAULT 'pending',
    result JSONB,
    error_message TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP
);

-- Search vector index for notes
ALTER TABLE notes ADD COLUMN search_vector TSVECTOR;
CREATE INDEX idx_notes_search ON notes USING GIN(search_vector);
```

---

## Relationships Diagram

```
┌──────────┐         ┌─────────┐
│  User    │◄───────►│  Note   │
│ (OAuth)  │ 1:N     │         │
└──────────┘         └┬────┬───┘
                     │    │
                     │    └─────┐
                     │          │
                     ▼          ▼
              ┌──────────┐  ┌──────────┐
              │  Tag     │  │Version   │
              │          │  │          │
              └────┬─────┘  └──────────┘
                   │
                   ▼
             ┌──────────┐
             │NoteTag   │ (M:N)
             └────┬─────┘
                  │
                  ▼
            ┌──────────┐
            │Attachment │
            │           │
            └──────────┘

┌──────────┐         ┌──────────┐
│  User    │◄───────►│APIToken  │
└──────────┘ 1:N     │          │
                     └──────────┘

┌──────────┐         ┌──────────┐
│  Note    │◄───────►│ AITask   │
└──────────┘ 1:N     │          │
                     └──────────┘
```
