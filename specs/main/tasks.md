---
description: "Task list for Notes App with AI Integration feature"
---

# Tasks: Notes App with AI Integration

**Input**: Design documents from `/specs/bubbles-notes/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Tests are MUST REQUIRED. Aim for 100% coverage, including negative scenarios and edge cases. Additionally, create integration test-style unit tests as well that verifies functionality across components.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Single project**: `src/`, `tests/` at repository root
- Paths shown below assume single project structure per plan.md

---

## Build Verification Rules

**CRITICAL**: After completing each task, verify the build compiles successfully:

```bash
# Run Kotlin compilation check
gradlew.bat compileKotlin --console=plain

# If errors occur, fix them before marking task complete
# Do NOT proceed to next task until build passes
```

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [x] T001 Create frontend directory structure with React + Vite configuration
- [x] T002 Initialize npm project in `frontend/` with React 18, Vite, TypeScript dependencies
- [x] T003 Configure Vite build to output to `frontend/build/` for Ktor bundling
- [x] T004 Create Kotlin package structure: `src/main/kotlin/models/`, `src/main/kotlin/services/`, `src/main/kotlin/api/`
- [x] T005 [P] Configure Ktor static resources plugin in `Application.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T006 Setup database schema with users, notes, tags, attachments tables per data-model.md
- [x] T007 Create User entity model in `src/main/kotlin/models/User.kt`
- [x] T008 Create Note entity model in `src/main/kotlin/models/Note.kt`
- [x] T009 Create Tag entity and join table models in `src/main/kotlin/models/Tag.kt`
- [x] T010 Create Attachment entity model in `src/main/kotlin/models/Attachment.kt`
- [x] T011 Create Version entity model for note history tracking in `src/main/kotlin/models/Version.kt`
- [x] T012 Setup Ktor authentication plugin (simplified - no OAuth) in `Security.kt`
- [ ] T013 Implement encrypted session storage for authentication tokens
- [x] T014 Create API key generation and validation service in `src/main/kotlin/services/ApiKeyService.kt`
- [x] T015 Configure database connection pool with HikariCP in `Databases.kt`
- [ ] T018 [Infrastructure] Add profile-based database configuration (H2 for dev, PostgreSQL for prod) in `application.yaml`
- [ ] T019 [Infrastructure] Create `application-dev.yaml` with H2 in-memory database settings
- [ ] T020 [Infrastructure] Create `application-prod.yaml` with PostgreSQL connection settings
- [ ] T016 Setup dependency injection module in `src/main/kotlin/GuiceModules.kt` to properly configure NoteRepository for NotesApi
- [x] T017 Implement server-side encryption service using centralized key in `src/main/kotlin/services/EncryptionService.kt`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Quick Note Creation (Priority: P1) 🎯 MVP

**Goal**: Provide immediate note creation capability via persistent editor at top of dashboard

**Independent Test**: User opens the dashboard and can immediately type in an editor at the top without clicking any buttons.

### Implementation Tasks

- [x] T018 [US1] Add Markdown preview rendering using react-markdown in `NoteEditor.tsx`
- [x] T019 [US1] Create note creation API endpoint POST `/api/v1/notes` in `src/main/kotlin/api/NotesApi.kt`
- [x] T020 [US1] Implement note creation service in `src/main/kotlin/services/note_service.kt`
- [x] T021 [US1] Add database INSERT query for new notes with auto-generated ID
- [x] T022 [US1] Create React dashboard layout component in `frontend/src/pages/Dashboard.tsx`
- [x] T023 [US1] Implement sticky note editor at top of dashboard grid in `Dashboard.tsx`

### UI/UX Tasks

- [x] T024 [US1] Add CSS styling for note editor with focus states and auto-save indicator
- [x] T025 [US1] Implement keyboard shortcuts: Ctrl+S to save, Enter to finish editing

---

## Phase 4: UI Design & Component Library (Priority: P0) 🎨

**Purpose**: Establish professional visual design system and reusable component library before implementing complex user stories

**Goal**: Create a polished, consistent UI with proper styling, animations, and accessibility across all components

### Design System Setup

- [x] T026 Configure Tailwind CSS with custom theme colors and typography in `tailwind.config.js`
- [x] T027 Create design tokens file (`frontend/src/styles/tokens.css`) with color palette
- [x] T028 Set up global styles and CSS variables in `index.css`
- [x] T029 Install and configure @headlessui/react for accessible components
- [x] T030 Install @heroicons/react for consistent iconography

### Core UI Components

- [x] T031 Create Button component (`frontend/src/components/ui/Button.tsx`) with variants (primary, secondary, ghost, danger)
- [x] T032 Create Input component (`frontend/src/components/ui/Input.tsx`) with validation states
- [x] T033 Create Card component (`frontend/src/components/ui/Card.tsx`) with hover effects
- [x] T034 Create Modal/Dialog component (`frontend/src/components/ui/Modal.tsx`) with animations
- [x] T035 Create Loading components (`frontend/src/components/ui/Loading.tsx`) - spinner and skeleton loaders
- [x] T036 Create Toast notification system (`frontend/src/components/ui/Toast.tsx`)

### Layout Components

- [x] T037 Create Dashboard layout wrapper with responsive grid in `frontend/src/components/Layout.tsx`
- [x] T038 Create Navigation header component (`frontend/src/components/Header.tsx`)
- [ ] T039 Create responsive container component for consistent max-width layouts

### Component Styling & Polish

- [x] T040 Style NoteEditor with professional focus states, auto-save indicator, and markdown toolbar
- [x] T041 Style Dashboard note cards with hover effects, transitions, and proper spacing
- [ ] T042 Style tag chips with remove functionality and consistent colors
- [ ] T043 Style search bar with autocomplete dropdown and clear button
- [ ] T044 Add smooth animations using CSS transitions (fadeIn, slideUp)
- [ ] T045 Implement responsive breakpoints for mobile, tablet, and desktop views

### Accessibility & UX

- [ ] T046 Add keyboard navigation support across all interactive elements
- [ ] T047 Implement focus management in modals and dialogs
- [ ] T048 Add ARIA labels to all interactive elements
- [ ] T049 Ensure color contrast ratios meet WCAG 2.1 AA standards
- [ ] T050 Test responsive design on multiple device sizes

---

## Phase 5: User Story 2 - Google OAuth Authentication (Priority: P1)

**Goal**: Enable user authentication via Google account for secure data isolation

**Independent Test**: User can click "Sign in with Google" and successfully authenticate, gaining access to their personal notes.

### Implementation Tasks

- [ ] T026 [US2] Configure Google OAuth client ID and redirect URI in `application.yaml`
- [ ] T027 [US2] Implement OAuth callback handler GET `/auth/google/callback` in `Security.kt`
- [ ] T028 [US2] Create user lookup/creation logic based on Google profile data
- [ ] T029 [US2] Generate and store encryption salt per user in User entity
- [ ] T030 [US2] Implement session cookie management with encrypted token storage
- [ ] T031 [US2] Add logout endpoint POST `/auth/logout` in `Security.kt`
- [ ] T032 [US2] Create React login button component in `frontend/src/components/LoginButton.tsx`
- [ ] T033 [US2] Implement OAuth redirect flow in `LoginButton.tsx`

### Security Tasks

- [ ] T034 [US2] Add CSRF protection token generation and validation
- [ ] T035 [US2] Implement token refresh mechanism for expired OAuth tokens

---

## Phase 6: User Story 3 - Markdown Note Editing (Priority: P2)

**Goal**: Support Markdown formatting in note editing with real-time preview

**Independent Test**: User can type Markdown syntax in the editor and see it rendered correctly as formatted content.

### Implementation Tasks

- [ ] T036 [US3] Update NoteEditor component to use react-markdown for rendering
- [ ] T037 [US3] Add remark-gfm plugin support for GitHub Flavored Markdown
- [ ] T038 [US3] Implement rehype-sanitize for XSS protection in rendered content
- [ ] T039 [US3] Create Markdown editor state management with draft preservation
- [ ] T040 [US3] Add version tracking when note content changes (for history view)

### UI/UX Tasks

- [ ] T041 [US3] Implement split-view editor with live preview toggle
- [ ] T042 [US3] Add Markdown toolbar with common formatting buttons

### Backend Integration Tasks

- [ ] T108 [Integration] Initialize noteRepository in Application.kt routing setup
- [ ] T109 [Integration] Connect frontend NoteEditor onSave to actual backend POST /api/v1/notes
- [ ] T110 [Integration] Implement auto-save mechanism with debounced API calls
- [ ] T111 [Integration] Add proper error handling and user feedback for save failures

---

## Phase 7: User Story 4 - URL Preview Generation (Priority: P2)

**Goal**: Automatically generate preview cards for URLs in note content

**Independent Test**: When a note contains a URL, a preview card appears showing the title, description, and favicon of the linked page.

### Implementation Tasks

- [ ] T043 [US4] Create URL detection regex pattern for Markdown content
- [ ] T044 [US4] Implement HTTP client for fetching URL metadata (title, description, favicon)
- [ ] T045 [US4] Add 5-second timeout per URL fetch to prevent blocking
- [ ] T046 [US4] Create URL preview cache service in `src/main/kotlin/services/url_cache_service.kt`
- [ ] T047 [US4] Store preview data as JSONB in notes table (preview_data column)
- [ ] T048 [US4] Update Note entity model to include preview_data field

### UI/UX Tasks

- [ ] T049 [US4] Create React URLPreview component in `frontend/src/components/URLPreview.tsx`
- [ ] T050 [US4] Display preview card with title, description, and favicon
- [ ] T051 [US4] Handle fetch errors gracefully (show raw URL if preview fails)

---

## Phase 8: User Story 5 - File Attachments (Priority: P2)

**Goal**: Allow attaching files to notes with server-side encryption during upload/download

**Independent Test**: User can upload a file and it appears as an attachment in the note that can be downloaded.

### Implementation Tasks

- [ ] T052 [US5] Create multipart form data handler for file uploads in `src/main/kotlin/api/AttachmentsApi.kt`
- [ ] T053 [US5] Implement server-side encryption using centralized key (AES-GCM)
- [ ] T054 [US5] Add configurable max attachment size via environment variable
- [ ] T055 [US5] Create Attachment entity storage with encrypted data in database
- [ ] T056 [US5] Implement file download endpoint GET `/api/v1/notes/{id}/attachments/{attachmentId}`
- [ ] T057 [US5] Add decryption during file download using centralized encryption key

### UI/UX Tasks

- [ ] T058 [US5] Create React FileUpload component in `frontend/src/components/FileUpload.tsx`
- [ ] T059 [US5] Display uploaded files as attachment chips with filename and type icon
- [ ] T060 [US5] Implement file preview for images and PDFs

---

## Phase 9: User Story 6 - Tagging System (Priority: P3)

**Goal**: Support adding, displaying, and filtering by tags on notes

**Independent Test**: User can add tags to a note and later filter or search by those tags.

### Implementation Tasks

- [ ] T061 [US6] Create Tag entity model with user_id and name (unique per user)
- [ ] T062 [US6] Create NoteTag join table for many-to-many relationship
- [ ] T063 [US6] Add tag management API endpoints in `src/main/kotlin/api/TagsApi.kt`
- [ ] T064 [US6] Implement tag filtering query for note listing
- [ ] T065 [US6] Create Tag entity service in `src/main/kotlin/services/tag_service.kt`

### UI/UX Tasks

- [ ] T066 [US6] Create React ChipInput component in `frontend/src/components/ChipInput.tsx`
- [ ] T067 [US6] Implement chip input interface (type tag name + Enter to add)
- [ ] T068 [US6] Display tags as clickable chips on note cards
- [ ] T069 [US6] Add tag filter dropdown in dashboard for filtering notes by tag

---

## Phase 10: User Story 7 - Content Search (Priority: P3)

**Goal**: Enable search across note content, tags, and attachment filenames

**Independent Test**: User types a search query and sees results matching the text in their notes, tags, or file names.

### Implementation Tasks

- [ ] T070 [US7] Add tsvector column to notes table for full-text search
- [ ] T071 [US7] Create PostgreSQL GIN index on search_vector
- [ ] T072 [US7] Implement search API endpoint POST `/api/v1/search` in `src/main/kotlin/api/SearchApi.kt`
- [ ] T073 [US7] Add tag name matching to search query
- [ ] T074 [US7] Add attachment filename matching to search query

### UI/UX Tasks

- [ ] T075 [US7] Create React SearchBar component in `frontend/src/components/SearchBar.tsx`
- [ ] T076 [US7] Display search results with snippet highlighting
- [ ] T077 [US7] Add search query parameter to note listing API

---

## Phase 11: User Story 8 - AI-Powered Note Enhancement (Priority: P4)

**Goal**: Automatically generate summaries, titles, and tag suggestions using AI

**Independent Test**: After saving a note, an AI-generated title and summary appear, and tags are suggested based on content.

### Implementation Tasks

- [ ] T078 [US8] Create AI task queue table (ai_tasks) for async processing
- [ ] T079 [US8] Implement OpenAI API client in `src/main/kotlin/services/openai_client.kt`
- [ ] T080 [US8] Add async job scheduler to process pending AI tasks
- [ ] T081 [US8] Update Note entity with ai_title, ai_summary, ai_tags fields
- [ ] T082 [US8] Implement graceful degradation when AI service unavailable

### UI/UX Tasks

- [ ] T083 [US8] Display AI-generated title and summary in note view
- [ ] T084 [US8] Show pending status indicator while AI processing
- [ ] T085 [US8] Add tag suggestions as clickable chips

---

## Phase 12: User Story 9 - Infinite Scroll with Lazy Loading (Priority: P4)

**Goal**: Load notes progressively as user scrolls for responsive UI

**Independent Test**: User scrolls down and more notes automatically appear without manual pagination controls.

### Implementation Tasks

- [ ] T086 [US9] Implement cursor-based pagination in note listing API
- [ ] T087 [US9] Add next_cursor token to paginated responses
- [ ] T088 [US9] Create client-side cache for loaded notes in React
- [ ] T089 [US9] Implement IntersectionObserver for scroll detection

### UI/UX Tasks

- [ ] T090 [US9] Create React InfiniteScroll component in `frontend/src/components/InfiniteScroll.tsx`
- [ ] T091 [US9] Add loading spinner while fetching next page
- [ ] T092 [US9] Preserve scrolled position when new notes load

---

## Phase 13: User Story 10 - Programmatic API Access (Priority: P5)

**Goal**: Provide REST APIs for programmatic note access via API key

**Independent Test**: User provides their API key in HTTP headers and successfully creates or retrieves notes programmatically.

### Implementation Tasks

- [ ] T093 [US10] Add X-API-Key header authentication middleware
- [ ] T094 [US10] Implement API token validation against api_tokens table
- [ ] T095 [US10] Create API-only note endpoints (no session cookie required)
- [ ] T096 [US10] Add rate limiting per API key

### UI/UX Tasks

- [ ] T097 [US10] Create React APIKeyManager component in `frontend/src/components/APIKeyManager.tsx`
- [ ] T098 [US10] Display generated API keys with copy-to-clipboard functionality
- [ ] T099 [US10] Add API documentation page

---

## Phase 14: Polish & Cross-Cutting Concerns

**Purpose**: Final polish and non-functional requirements

### Performance Optimization

- [ ] T100 [P] Implement client-side caching with 30-second TTL for note lists
- [ ] T101 [P] Add database query optimization (indexes, prepared statements)
- [ ] T102 [P] Implement connection pooling for database connections

### Error Handling & Logging

- [ ] T103 [P] Add comprehensive error handling with user-friendly messages
- [ ] T104 [P] Implement structured logging with correlation IDs

### Documentation

- [ ] T105 [P] Update API documentation in `contracts/api.yaml` with all endpoints
- [ ] T106 [P] Create developer setup guide for frontend development
- [ ] T107 [P] Configure Gradle Node plugin for React build integration in `build.gradle.kts`

---

## Dependencies Graph

```
Phase 1 (Setup) ──┐
                  ├→ Phase 2 (Foundational) ──┬→ Phase 3 (US1: Quick Note Creation)
                  │                           ├→ Phase 4 (UI Design & Component Library)
                  │                           ├→ Phase 5 (US2: OAuth Auth)
                  │                           ├→ Phase 6 (US3: Markdown Editing)
                  │                           ├→ Phase 7 (US4: URL Preview)
                  │                           ├→ Phase 8 (US5: File Attachments)
                  │                           ├→ Phase 9 (US6: Tagging System)
                  │                           ├→ Phase 10 (US7: Content Search)
                  │                           ├→ Phase 11 (US8: AI Enhancement)
                  │                           ├→ Phase 12 (US9: Infinite Scroll)
                  │                           └→ Phase 13 (US10: API Access)
                  └→ Phase 14 (Polish & Cross-Cutting)
```

## Parallel Execution Examples

Per user story, tasks marked with [P] can run in parallel:

**Foundational Phase**:
- T006 (Database schema) and T012 (OAuth setup) can run in parallel
- T007-T011 (Entity models) can all run in parallel

**User Story Phases**:
- UI components and API endpoints for each story can be developed independently
- Database migrations can run alongside service implementation

## Implementation Strategy

### MVP Scope (Recommended First Release)
- Phase 1: Setup
- Phase 2: Foundational
- Phase 3: User Story 1 - Quick Note Creation (P1)

This provides a working notes app with basic CRUD operations and immediate note creation.

### Incremental Delivery
After MVP, add user stories in priority order:
1. US2 (OAuth) - Enables multi-device sync
2. US3 (Markdown) - Improves content formatting
3. US4-US7 - Enhancement features
4. US8-US10 - Advanced features

---

## Summary

- **Total Tasks**: 132
- **Setup Phase**: 5 tasks
- **Foundational Phase**: 12 tasks (added Guice DI and server-side encryption)
- **UI Design Phase**: 25 tasks (T026-T050: design system, components, styling, accessibility)
- **User Story Phases**: 84 tasks (P1: 10, P2: 36, P3: 20, P4: 17, P5: 9)
- **Polish Phase**: 7 tasks (added build system integration)

**MVP Tasks**: T001-T025 (25 tasks)
