# Feature Specification: Notes App with AI Integration

**Feature Branch**: `bubbles-notes`  
**Created**: 2026-03-17  
**Last Updated**: 2026-03-18  
**Status**: Draft  
**Input**: "Build a notes app like google keep"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Quick Note Creation (Priority: P1)

As a user, I want to immediately start writing notes without any extra clicks, so that I can capture thoughts quickly and efficiently.

**Why this priority**: This is the core value proposition of the app - instant note creation is what makes Google Keep successful. Without this, users would need to navigate through multiple steps before capturing their thoughts.

**Independent Test**: User opens the dashboard and can immediately type in an editor at the top without clicking any buttons.

**Acceptance Scenarios**:

1. **Given** I am on the main dashboard, **When** the page loads, **Then** I see a note editor ready for input at the top of the screen
2. **Given** I am viewing my notes, **When** I scroll back to the top, **Then** the quick note editor is always visible and ready for input
3. **Given** I have finished writing a note, **When** I click outside or press save, **Then** the note is saved and appears in my notes grid

---

### User Story 2 - Google OAuth Authentication (Priority: P1)

As a user, I want to sign in using my Google account, so that I can securely access my notes across devices without managing separate credentials.

**Why this priority**: Authentication is the foundation for data isolation and multi-device sync. Without it, users cannot have personalized, secure note storage.

**Independent Test**: User can click "Sign in with Google" and successfully authenticate, gaining access to their personal notes.

**Acceptance Scenarios**:

1. **Given** I am on the landing page, **When** I click "Sign in with Google", **Then** I am redirected to Google's OAuth consent screen
2. **Given** I have authenticated with Google, **When** the callback completes, **Then** I am logged in and see my personalized notes dashboard
3. **Given** I am already logged in, **When** I revisit the app, **Then** I remain authenticated without needing to sign in again

---

### User Story 3 - Markdown Note Editing (Priority: P2)

As a user, I want to write notes using Markdown formatting, so that I can create well-structured content with headings, lists, and emphasis.

**Why this priority**: Markdown provides a lightweight yet powerful way to format notes without the complexity of rich text editors.

**Independent Test**: User can type Markdown syntax in the editor and see it rendered correctly as formatted content.

**Acceptance Scenarios**:

1. **Given** I am editing a note, **When** I type Markdown (e.g., `# Heading`, `*italic*`, `- list`), **Then** the preview shows properly formatted text
2. **Given** I have written a note with Markdown, **When** I save it, **Then** the formatting is preserved when I view the note later
3. **Given** I am viewing a saved note, **When** the note contains Markdown, **Then** it renders as formatted content rather than raw text

---

### User Story 4 - URL Preview Generation (Priority: P2)

As a user, I want URLs in my notes to automatically generate preview cards with site information, so that shared links are more informative and visually appealing.

**Why this priority**: URL previews enhance the value of shared content and make notes with references more useful at a glance.

**Independent Test**: When a note contains a URL, a preview card appears showing the title, description, and favicon of the linked page.

**Acceptance Scenarios**:

1. **Given** I have saved a note containing `https://example.com`, **When** I view the note, **Then** I see a preview card with the website's title and description
2. **Given** a URL is detected in my note, **When** the preview loads, **Then** I see the favicon of the linked site
3. **Given** a URL preview fails to load, **When** the error occurs, **Then** the note still displays normally with just the raw URL

---

### User Story 5 - File Attachments (Priority: P2)

As a user, I want to attach any type of file to my notes, so that I can store documents, images, and other resources alongside my text.

**Why this priority**: Notes often reference or contain supporting files; the ability to attach them makes the app a complete knowledge repository.

**Independent Test**: User can upload a file (PDF, image, document) and it appears as an attachment in the note that can be downloaded or viewed.

**Acceptance Scenarios**:

1. **Given** I am editing a note, **When** I attach a file, **Then** the file appears as an attachment with its name and type icon
2. **Given** a note has attachments, **When** I view the note, **Then** I can download or preview each attached file
3. **Given** I attach multiple files, **When** I save the note, **Then** all attachments persist with the note

---

### User Story 6 - Tagging System (Priority: P3)

As a user, I want to add tags to my notes, so that I can organize and categorize them for easier retrieval.

**Why this priority**: Tags provide flexible organization beyond folders, enabling cross-cutting categorization of notes.

**Independent Test**: User can add tags to a note and later filter or search by those tags.

**Acceptance Scenarios**:

1. **Given** I am editing a note, **When** I add tags (e.g., `#work`, `#personal`), **Then** the tags are saved with the note
2. **Given** I have notes with tags, **When** I filter by a tag, **Then** only notes with that tag are shown
3. **Given** a note has multiple tags, **When** I view the note, **Then** all tags are visible and clickable

---

### User Story 7 - Content Search (Priority: P3)

As a user, I want to search across note content, tags, and attachment names, so that I can quickly find any information I need.

**Why this priority**: Search is essential for navigating a growing collection of notes; comprehensive search ensures nothing gets lost.

**Independent Test**: User types a search query and sees results matching the text in their notes, tags, or file names.

**Acceptance Scenarios**:

1. **Given** I have notes with various content, **When** I search for a keyword, **Then** notes containing that keyword appear in results
2. **Given** I have tagged notes, **When** I search by tag name, **Then** notes with that tag are included in results
3. **Given** I have attachments, **When** I search by filename, **Then** notes containing those files appear in results

---

### User Story 8 - AI-Powered Note Enhancement (Priority: P4)

As a user, I want my notes to be automatically summarized and titled by AI, so that I can quickly understand the content without reading everything.

**Why this priority**: AI automation reduces cognitive load and makes large note collections more manageable.

**Independent Test**: After saving a note, an AI-generated title and summary appear, and tags are suggested based on content.

**Acceptance Scenarios**:

1. **Given** I have saved a new note, **When** the AI processes it, **Then** a concise summary is generated
2. **Given** a note has no explicit title, **When** AI analyzes it, **Then** an appropriate title is suggested based on content
3. **Given** a note's content, **When** AI processes it, **Then** relevant tags are automatically added

---

### User Story 9 - Infinite Scroll with Lazy Loading (Priority: P4)

As a user, I want notes to load progressively as I scroll, so that the interface remains responsive even with many notes.

**Why this priority**: Lazy loading ensures smooth performance and seamless UX when dealing with large note collections.

**Independent Test**: User scrolls down and more notes automatically appear without manual pagination controls.

**Acceptance Scenarios**:

1. **Given** I have many notes, **When** I scroll to the bottom of the current view, **Then** additional notes load automatically
2. **Given** notes are loading, **When** the request completes, **Then** new notes appear seamlessly in the grid
3. **Given** I have scrolled through multiple pages, **When** I scroll back up, **Then** previously loaded notes remain visible (cached)

---

### User Story 10 - Programmatic API Access (Priority: P5)

As a developer user, I want to access my notes via REST APIs with an API key, so that I can integrate note creation and retrieval into other applications.

**Why this priority**: API access enables automation and integration, extending the app's utility beyond the web interface.

**Independent Test**: User provides their API key in HTTP headers and successfully creates or retrieves notes programmatically.

**Acceptance Scenarios**:

1. **Given** I have an API key, **When** I make a POST request to create a note with proper authentication, **Then** the note is created
2. **Given** I have an API key, **When** I make a GET request to list notes, **Then** I receive a paginated list of my note IDs and metadata
3. **Given** I have an API key, **When** I make a GET request for a specific note, **Then** I receive the full note content

---

## Clarifications

### Session 2026-03-18

- **Q1: File attachment storage location** → A: Files stored locally, encrypted during upload and decrypted during download using configurable encryption key (hardcoded for now, future AWS KMS integration); maximum attachment size must be configurable via environment variable
- **Q2: AI-powered note enhancement behavior** → A: Auto-generated asynchronously - notes save immediately without waiting for AI; summaries, titles, and tags appear later when user views the note after background processing completes
- **Q3: Concurrent edit handling** → A: Last-write-wins strategy with auto-save enabled. Notes must always auto-save (no manual save button). Versioning system required to view historical versions. New notes show unpublished draft if user navigates away without posting.
- **Q4: URL preview generation timing** → A: Automatic - Preview cards generate immediately when URLs are detected and saved in note content
- **Q5: Tag input format** → A: Chip input - User types tag name and presses Enter to add each tag as a visual chip

## Edge Cases

- What happens when Google OAuth returns an error or user cancels authentication?
- How does the system handle very large attachments (e.g., 100MB+ files)?
- What occurs when AI service is unavailable - should notes still save without AI enhancement?
- How are duplicate URLs handled in URL preview generation?
- What happens when search returns no results?
- How does the system handle concurrent edits to the same note?
- What is the behavior when the database connection fails in production mode?
- How are file type restrictions handled (if any)?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a dashboard layout with a persistent note editor at the top for quick note creation
- **FR-002**: System MUST authenticate users exclusively via Google OAuth
- **FR-003**: System MUST support Markdown rendering in note editing and display
- **FR-004**: System MUST detect URLs in note content and generate preview cards with title, description, and favicon
- **FR-005**: System MUST allow attaching files of any type to notes; files MUST be encrypted during upload and decrypted during download using configurable encryption key (hardcoded for now, future AWS KMS integration); maximum attachment size must be configurable via environment variable
- **FR-006**: System MUST support adding, displaying, and filtering by tags on notes
- **FR-007**: System MUST provide search functionality across note content, tags, and attachment filenames
- **FR-008**: System MUST generate AI-powered summaries, titles, and tag suggestions for notes using OpenAI-compatible API; processing occurs asynchronously after note save (non-blocking)
- **FR-009**: System MUST implement infinite scroll with lazy loading for the notes grid
- **FR-010**: System MUST provide REST APIs for programmatic note creation, retrieval, and listing (authenticated via per-user API key)
- **FR-011**: System MUST support both PostgreSQL (production) and SQLite (testing/local) database backends based on configuration
- **FR-012**: System MUST implement client-side caching to avoid unnecessary refetching of notes
- **FR-014**: System MUST implement versioning to track note changes over time, allowing users to view historical versions
- **FR-015**: New notes must preserve unpublished draft content when user navigates away before posting
- **FR-016**: Tags MUST be added via chip input interface (type tag name and press Enter)

### Non-Functional Requirements

- **NFR-001**: All user data MUST be encrypted at rest using keys derived from OAuth identity tokens (per Constitution Security Principle)
- **NFR-002**: API responses for list operations MUST include pagination metadata (total count, page size, next cursor/token)
- **NFR-003**: The application MUST gracefully degrade when AI service is unavailable (notes save without enhancement)
- **NFR-004**: File uploads MUST have configurable size limits via environment variable; files MUST be encrypted during upload and decrypted during download using configurable encryption key (hardcoded for now, future AWS KMS integration)
- **NFR-005**: The frontend MUST be precompiled and bundled with the Ktor backend for single-deployment delivery

### Key Entities

- **Note**: Core data entity containing title, content (Markdown), tags, attachments, AI-generated summary, timestamps, owner reference, and version history for tracking changes
- **User**: Authentication entity derived from Google OAuth profile, includes unique identifier and API key
- **Tag**: Categorization entity linked to notes, supports many-to-many relationships; tags added via chip input interface (type name + Enter)
- **Attachment**: File metadata entity including filename, type, size, storage path, and parent note reference
- **APIKey**: Per-user authentication credential for programmatic access

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can create a new note within 2 seconds of opening the dashboard (measured from page load to first character typed)
- **SC-002**: The system supports at least 10,000 notes per user without UI degradation
- **SC-003**: Search results return in under 500ms for queries across up to 1,000 notes
- **SC-004**: URL previews load within 2 seconds for standard websites (95th percentile)
- **SC-005**: Mobile and desktop views achieve Lighthouse performance score of 80+ on both platforms
- **SC-006**: API endpoints respond in under 300ms for note listing operations with pagination
- **SC-007**: Users can successfully authenticate via Google OAuth in under 5 seconds (from click to dashboard)

## Assumptions

1. File attachments stored locally with encryption during upload/download using configurable key (hardcoded now, AWS KMS future); maximum size configurable via environment variable
2. AI summarization is asynchronous - notes save immediately, AI enhancement appears after background processing completes
3. URL preview fetching will timeout after 5 seconds per URL to prevent blocking; previews generate automatically when URLs are saved
4. Tags are case-insensitive and automatically normalized (lowercase, trimmed); added via chip input interface
5. The "PRODUCTION" boolean flag controls database selection: true = PostgreSQL, false = SQLite
6. API key generation occurs upon first authentication or explicit user request
7. Client-side caching will use a time-based strategy (e.g., 30-second cache for note lists)
8. Auto-save frequency balances network load with data safety (implementation detail to be determined in planning)
9. Last-write-wins strategy for concurrent edits; versioning system tracks all changes

## Environment Variables Required

| Variable | Description | Required |
|----------|-------------|----------|
| `GOOGLE_OAUTH_CLIENT_ID` | Google OAuth client ID | Yes |
| `GOOGLE_OAUTH_CLIENT_SECRET` | Google OAuth client secret | Yes |
| `DB_HOST` | PostgreSQL host address | Conditional (production) |
| `DB_PORT` | PostgreSQL port | Conditional (production) |
| `DB_USERNAME` | Database username | Conditional (production) |
| `DB_PASSWORD` | Database password | Conditional (production) |
| `DB_DATABASE_NAME` | Database name | Conditional (production) |
| `PRODUCTION` | Boolean flag for production mode | Yes |
| `AI_API_URL` | OpenAI-compatible API endpoint URL | Conditional (AI features) |
| `AI_MODEL_ID` | Model identifier for AI service | Conditional (AI features) |
| `MAX_ATTACHMENT_SIZE` | Maximum file upload size in bytes | No (default: 25MB) |

## Technical Constraints (from Constitution)

- All user-domain data MUST be encrypted at rest with keys derived from OAuth identity tokens
- Encryption keys MUST NEVER be stored on the server side
- System MUST use Google Guice for dependency injection to ensure testability
- Testing stack must include JUnit 5 and MockK
- All services must accept dependencies via constructor injection
