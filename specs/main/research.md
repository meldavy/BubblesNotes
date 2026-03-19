# Research: Notes App with AI Integration

**Branch**: `bubbles-notes` | **Date**: 2026-03-18

## Decision Log

### 1. Google OAuth Integration with Ktor

**Decision**: Use Ktor's `oauth` plugin with Google provider, storing tokens in encrypted session cookies.

**Rationale**: 
- Ktor's oauth plugin provides built-in support for OAuth flows
- Session-based authentication with encrypted cookies is secure and standard
- Tokens stored server-side in encrypted form (not in client cookies directly)

**Alternatives considered**:
- JWT tokens: Rejected due to token refresh complexity and stateless nature requiring token blacklisting
- Direct token storage in localStorage: Rejected due to XSS vulnerability

### 2. Client-Side Encryption with User-Derived Keys

**Decision**: Use Web Crypto API (AES-GCM) for client-side encryption, key derived from OAuth token via PBKDF2.

**Rationale**:
- Web Crypto API is standard and available in all modern browsers
- AES-GCM provides authenticated encryption (confidentiality + integrity)
- PBKDF2 with salt derived from OAuth identity ensures deterministic keys per user

**Implementation details**:
```
Key Derivation: PBKDF2(HMAC-SHA256, oauthToken, salt, iterations=100000, keyLength=32)
Encryption: AES-GCM-256 with random IV per file
Storage: Encrypted files stored in database as BLOB
```

**Alternatives considered**:
- Server-side encryption only: Rejected because it violates Constitution Security Principle (server could access user data)
- Hybrid encryption (RSA for key exchange): Rejected due to complexity and performance overhead

### 3. Markdown Rendering in React

**Decision**: Use `react-markdown` with `remark-gfm` plugin for rendering, `marked` for preview editing.

**Rationale**:
- react-markdown is actively maintained and well-tested
- remark plugins provide extensibility (GFM, tables, task lists)
- Security: Use `rehype-sanitize` to prevent XSS attacks

**Implementation details**:
```typescript
// Rendering
import ReactMarkdown from 'react-markdown'
import rehypeSanitize from 'rehype-sanitize'

<ReactMarkdown rehypePlugins={[rehypeSanitize]}>{content}</ReactMarkdown>

// Editing (preview)
import { marked } from 'marked'
marked(markdownContent)
```

**Alternatives considered**:
- `react-mde`: Rejected due to heavy dependencies and less flexible
- Custom parser: Rejected due to maintenance burden

### 4. Async Job Processing for AI Enhancement

**Decision**: Use PostgreSQL's `pg_notify` with a job queue table, processed by Ktor background coroutine.

**Rationale**:
- Single database connection pool (no additional infrastructure)
- pg_notify provides real-time notifications for job processing
- Ktor coroutines handle async processing without blocking requests

**Database schema**:
```sql
CREATE TABLE ai_jobs (
    id SERIAL PRIMARY KEY,
    note_id INT NOT NULL REFERENCES notes(id),
    status VARCHAR(20) DEFAULT 'pending',  -- pending, processing, completed, failed
    result JSONB,  -- AI-generated title, summary, tags
    error TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    processed_at TIMESTAMP
);

CREATE INDEX idx_ai_jobs_status ON ai_jobs(status);
```

**Alternatives considered**:
- Redis + Celery: Rejected due to infrastructure complexity (additional service required)
- Ktor scheduled tasks only: Rejected because no real-time notification mechanism

### 5. File Upload with Encryption

**Decision**: Client-side encryption using Web Crypto API before multipart upload.

**Rationale**:
- Files never stored unencrypted on server
- Multipart form data is standard and well-supported by Ktor
- Encryption key derived from user's OAuth token (deterministic per user)

**Implementation details**:
```typescript
// Client-side
const encryptedData = await encrypt(file, userEncryptionKey)
const formData = new FormData()
formData.append('file', new Blob([encryptedData]), file.name)

// Server-side
val encryptedBytes = call.receive<ByteArray>()
val decryptedBytes = decrypt(encryptedBytes, userKey)
```

**Alternatives considered**:
- Chunked upload with encryption: Rejected due to complexity in reassembling chunks
- Pre-signed S3 URLs with client-side encryption: Rejected due to AWS dependency

### 6. Infinite Scroll Implementation

**Decision**: Cursor-based pagination with `LIMIT` and `WHERE id > lastId`, cache last 50 notes in memory.

**Rationale**:
- Cursor-based is more efficient than offset for large datasets
- In-memory cache prevents refetching scrolled-back items
- Simple implementation without external dependencies

**Implementation details**:
```sql
-- First page
SELECT * FROM notes WHERE user_id = ? ORDER BY created_at DESC LIMIT 20;

-- Next pages
SELECT * FROM notes WHERE user_id = ? AND id < ? ORDER BY created_at DESC LIMIT 20;
```

**Alternatives considered**:
- Offset-based pagination: Rejected because performance degrades with large offsets
- Virtual scrolling (react-window): Rejected for initial implementation due to complexity

### 7. Search Implementation

**Decision**: PostgreSQL full-text search with tsvector index, fallback to LIKE for tags/attachments.

**Rationale**:
- PostgreSQL full-text search is fast and doesn't require external service
- tsvector index provides efficient text searching
- Simple fallback for tags (exact match) and attachments (filename search)

**Database schema**:
```sql
ALTER TABLE notes ADD COLUMN search_vector TSVECTOR;
CREATE INDEX idx_notes_search ON notes USING GIN(search_vector);

-- Trigger to update search vector on insert/update
UPDATE notes SET search_vector = 
    setweight(to_tsvector('english', title), 'A') ||
    setweight(to_tsvector('english', content), 'B');
```

**Alternatives considered**:
- Elasticsearch: Rejected due to infrastructure complexity
- Client-side search: Rejected because it doesn't scale (would require loading all notes)

### 8. Database Selection (PostgreSQL vs SQLite)

**Decision**: Keep existing H2 for testing, add PostgreSQL for production via configuration flag.

**Rationale**:
- Existing project already has H2 configured for testing
- PostgreSQL provides full-text search and advanced features for production
- Configuration-based selection allows easy local development

**Implementation**:
```yaml
# application.yaml
production: false  # true = PostgreSQL, false = H2 (SQLite-compatible)
```

### 9. Dependency Injection Pattern

**Decision**: Introduce Koin DI framework for service layer to enable testability.

**Rationale**:
- Ktor supports Koin integration
- Constructor injection enables easy mocking in tests
- Resolves current issue of direct instantiation in services

**Alternatives considered**:
- Manual DI: Rejected due to boilerplate
- Guice: Rejected because Kotlin-specific solution preferred

### 10. Frontend Framework Integration

**Decision**: Use React with Vite for development, precompile and bundle with Ktor.

**Rationale**:
- React is widely understood and has rich ecosystem
- Vite provides fast development server
- Precompiled bundles can be served as static resources by Ktor

**Implementation**:
```kotlin
// In Application.kt
staticResources("/app", "static")  // Serves precompiled React app
```

---

## Research Summary

| Component | Technology | Status |
|-----------|------------|--------|
| Authentication | Ktor OAuth + Encrypted Sessions | ✅ Decided |
| Encryption | Web Crypto API (AES-GCM) + PBKDF2 | ✅ Decided |
| Markdown | react-markdown + rehype-sanitize | ✅ Decided |
| Async Jobs | PostgreSQL pg_notify + Ktor coroutines | ✅ Decided |
| File Upload | Client-side encryption + Multipart | ✅ Decided |
| Pagination | Cursor-based with LIMIT | ✅ Decided |
| Search | PostgreSQL full-text search | ✅ Decided |
| DI Framework | Koin | ✅ Decided |
| Frontend | React + Vite (precompiled) | ✅ Decided |

---

## Build System Configuration

### React Frontend Integration

The React frontend will be built as a separate project within the repository and bundled with Ktor at build time.

**Directory Structure**:
```
frontend/
├── package.json
├── vite.config.ts
├── src/
│   ├── main.tsx
│   └── components/
└── build/          # Output directory (generated by npm run build)
```

### Build Process

1. **React Compilation**: `npm run build` in the `frontend/` directory generates static assets in `frontend/build/`
2. **Ktor Bundling**: The `buildFatJar` task copies React build output to `src/main/resources/static/`
3. **Final Artifact**: Single JAR containing both Kotlin backend and React frontend

### Implementation Plan for Gradle Build

**Approach**: Use the Gradle Node plugin to run npm commands during the build process.

```kotlin
// Add to build.gradle.kts (to be implemented)
plugins {
    id("com.github.node-gradle.node") version "7.0.1" apply false
}

tasks.register<Exec>("compileReact") {
    workingDir = File(project.projectDir, "frontend")
    commandLine("npm", "run", "build")
}

tasks.named<Jar>("jar") {
    dependsOn("compileReact")
    from(File(project.projectDir, "frontend/build")) {
        into("static")
    }
}
```

**Key Considerations**:
- React build runs before JAR creation
- Output directory `frontend/build/` is copied to Ktor's static resources path
- Build only proceeds if frontend dependencies are installed (npm install on first run)
