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
- [x] T013 Implement encrypted session storage for authentication tokens
- [x] T014 Create API key generation and validation service in `src/main/kotlin/services/ApiKeyService.kt`
- [x] T015 Configure database connection pool with HikariCP in `Databases.kt`
- [x] T018 [Infrastructure] Add single application.yaml config with environment variable overrides (H2 default, PostgreSQL via env vars)
- [x] T016 Setup dependency injection module in `src/main/kotlin/GuiceModules.kt` to properly configure NoteRepository for NotesApi
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
- [x] T039 Create responsive container component for consistent max-width layouts (Container, Grid in Layout.tsx)

### Component Styling & Polish

- [x] T040 Style NoteEditor with professional focus states, auto-save indicator, and markdown toolbar
- [x] T041 Style Dashboard note cards with hover effects, transitions, and proper spacing
- [x] T042 Style tag chips with remove functionality and consistent colors (`frontend/src/components/ui/TagChip.tsx`)
- [x] T043 Style search bar with autocomplete dropdown and clear button (`frontend/src/components/ui/SearchBar.tsx`)
- [x] T044 Add smooth animations using CSS transitions (fadeIn, slideUp) in `frontend/src/styles/animations.css`
- [x] T045 Implement responsive breakpoints for mobile, tablet, and desktop views (configured in tailwind.config.js)

### Accessibility & UX

- [x] T046 Add keyboard navigation support across all interactive elements (`frontend/src/styles/accessibility.css`)
- [x] T047 Implement focus management in modals and dialogs (Modal.tsx with focus trap)
- [x] T048 Add ARIA labels to all interactive elements (`frontend/src/styles/accessibility.css`)
- [ ] T049 Ensure color contrast ratios meet WCAG 2.1 AA standards
- [ ] T050 Test responsive design on multiple device sizes

---

## Phase 5: Login Flow Customer Experience (Priority: P1) 🎯

**Goal**: Implement complete login/logout customer experience with proper authentication flow, protected routes, and user state management.

**Problem Statement**: While backend OAuth infrastructure exists (Phase 6), the frontend lacks integration for a working login flow. Users cannot actually authenticate because:
- No auth context/provider to manage global authentication state
- Dashboard doesn't check auth status or redirect unauthenticated users  
- LoginButton component exists but is never integrated into the app
- No landing page for non-authenticated users
- No protected route wrapper to guard authenticated pages

**Independent Test**: User visits the app, sees a login prompt if not authenticated, can successfully sign in with Google, and gains access to their personalized dashboard. Upon logout, user returns to the landing/login state.

### Auth Infrastructure Tasks

- [x] T051 [Login] Create React Context for authentication state in `frontend/src/contexts/AuthContext.tsx`
  - Provide `useAuth()` hook with `isAuthenticated`, `user`, `isLoading`, `login()`, `logout()` values
  - Check auth status on mount via `/api/v1/auth/me` endpoint
  - Persist session state across page refreshes

### Landing Page Tasks

- [x] T052 [Login] Create landing page component in `frontend/src/pages/LandingPage.tsx`
  - Display app hero section with value proposition
  - Show "Sign in with Google" CTA button
  - Include feature highlights (quick notes, markdown, AI enhancement)
  - Handle both authenticated and unauthenticated states

- [x] T053 [Login] Update routing to show LandingPage for root `/` when not authenticated
  - If authenticated, redirect to Dashboard instead
  - Use simple hash-based routing or React Router if available

### Dashboard Integration Tasks

- [x] T054 [Login] Wrap Dashboard in ProtectedRoute in `frontend/src/App.tsx`
  - Ensure dashboard is only accessible when authenticated
  - Show loading state during auth check

- [x] T055 [Login] Create ProtectedRoute wrapper component in `frontend/src/components/ProtectedRoute.tsx`
  - Redirect to landing page if user is not authenticated
  - Show loading spinner while checking auth status
  - Allow access only when authenticated

### Header Integration Tasks

- [x] T056 [Login] Create UserProfile dropdown component in `frontend/src/components/UserProfile.tsx`
  - Show user avatar and email address when authenticated
  - Dropdown menu with logout option on click
  - Use Headless UI Menu for accessible dropdown behavior

- [x] T057 [Login] Update Header to conditionally render auth components in `frontend/src/components/Header.tsx`
  - When NOT authenticated: Render LoginButton component (for landing page)
  - When authenticated: Render UserProfile dropdown component (for dashboard)
  - Pass auth callbacks from AuthContext to both components

### UX Polish Tasks

- [x] T058 [Login] Add loading states during authentication checks across all components
  - Prevent flash of unauthenticated content
  - Show skeleton loaders while verifying session

- [x] T059 [Login] Implement redirect after login to original requested page
  - Preserve intended destination before OAuth redirect
  - Return user to same page after successful authentication

- [x] T060 [Login] Add error handling for OAuth failures with user-friendly messages
  - Handle Google OAuth errors gracefully
  - Show retry options when auth service is unavailable

- [x] T061 [Login] Implement automatic logout on session expiration in `frontend/src/contexts/AuthContext.tsx`
  - Detect 401 Unauthorized responses from backend API calls
  - Automatically clear auth state and redirect to landing page when OAuth token is no longer valid
  - Use fetch interceptor or wrapper function to catch auth errors globally

---

## Phase 6: User Story 2 - Google OAuth Authentication Backend (Priority: P1)

**Goal**: Backend infrastructure for Google OAuth authentication (already implemented, this phase documents the backend work).

**Independent Test**: Backend correctly handles OAuth callbacks, creates user records, manages sessions, and provides authenticated API access.

### Implementation Tasks

- [x] T026 [US2] Configure Google OAuth client ID and redirect URI in `application.yaml`
- [x] T027 [US2] Implement OAuth callback handler GET `/auth/google/callback` in `Security.kt`
- [x] T028 [US2] Create user lookup/creation logic based on Google profile data
- [x] T029 [US2] Generate and store encryption salt per user in User entity
- [x] T030 [US2] Implement session cookie management with encrypted token storage
- [x] T031 [US2] Add logout endpoint POST `/auth/logout` in `Security.kt`
- [x] T032 [US2] Create React login button component in `frontend/src/components/LoginButton.tsx`
- [x] T033 [US2] Implement OAuth redirect flow in `LoginButton.tsx`

### Security Tasks

- [x] T034 [US2] Add CSRF protection token generation and validation
- [x] T035 [US2] Implement token refresh mechanism for expired OAuth tokens
- [x] T132 [US2] Configure Ktor to return 401 Unauthorized for unauthenticated requests
   - Ensure all protected routes respond with HTTP 401 when session is invalid or missing
   - This enables frontend to detect session expiration and trigger auto-logout (T061)

### Test Coverage Tasks

- [x] T036 [US2] Create integration tests for SPA routing in `src/test/kotlin/com/mel/bubblenotes/AuthIntegrationTest.kt`
  - Verify `/dashboard`, `/settings`, `/profile` return 200 OK (serve index.html)
  - Verify API routes return 401 when not authenticated
- [x] T037 [US2] Add tests for authentication flow in `AuthIntegrationTest.kt`
  - Test logout endpoint clears session cookie
  - Test `/auth/me` returns 401 without valid session
  - Test OAuth callback returns 503 when not configured
- [x] T038 [Login] Fix frontend session cookie handling in `frontend/src/contexts/AuthContext.tsx`
  - Add `credentials: 'include'` to all fetch calls for session cookie transmission
  - Verify `/api/v1/auth/me` and `/api/v1/auth/userinfo` include cookies
- [x] T039 [Login] Configure CORS in Ktor for cross-origin frontend requests in `Security.kt`
  - Install CORS plugin with `anyHost()` and `allowCredentials = true`
  - Ensure session cookies work when frontend runs on different port

---

## Phase 7: Dashboard Integration (Priority: P1) 🎯

**Goal**: Wire up the Dashboard UI to load notes from the server and make note cards functional

**Problem Statement**: The Dashboard page exists but is not fully integrated with the backend:
- Notes are not loaded from the server on page load
- Note cards have non-functional edit and delete buttons
- No loading states during data fetching
- No error handling for API failures
- Missing infinite scroll implementation for progressive note loading

**Independent Test**: User logs in and sees their existing notes displayed as cards; clicking edit opens the note editor with pre-filled content; clicking delete removes the note from the list.

### Data Loading Tasks

- [x] T112 [Dashboard] Add `useEffect` hook in [`Dashboard.tsx`](frontend/src/pages/Dashboard.tsx:16) to fetch notes on component mount
  - Call `GET /api/v1/notes?limit=20` endpoint
  - Include `credentials: 'include'` for session cookie transmission
  - Populate `notes` state with returned data

- [x] T113 [Dashboard] Implement loading state in [`Dashboard.tsx`](frontend/src/pages/Dashboard.tsx:16)
  - Add `isLoading` state variable
  - Show skeleton loader or spinner while fetching notes
  - Prevent flash of empty state during initial load

- [x] T114 [Dashboard] Add error handling for note fetch failures
  - Add `error` state variable
  - Display user-friendly error message when API call fails
  - Add retry button for transient errors

### Note Card Functionality Tasks

- [x] T115 [Dashboard] Implement edit button handler in [`Dashboard.tsx`](frontend/src/pages/Dashboard.tsx:130)
  - Add onClick handler to edit button
  - Load note content into NoteEditor when edit is clicked
  - Scroll to top to show editor with pre-filled content

- [x] T116 [Dashboard] Implement delete button handler in [`Dashboard.tsx`](frontend/src/pages/Dashboard.tsx:138)
  - Call `DELETE /api/v1/notes/{id}` endpoint
  - Remove deleted note from local state after successful deletion
  - Add confirmation dialog before deletion

- [x] T117 [Dashboard] Add optimistic UI updates for delete operation
  - Immediately remove note from UI on delete click
  - Show undo toast notification
  - Rollback if delete API call fails

### Infinite Scroll Tasks

- [x] T118 [Dashboard] Implement cursor-based pagination in [`Dashboard.tsx`](frontend/src/pages/Dashboard.tsx:16)
  - Track `nextCursor` from API response
  - Load more notes when user scrolls near bottom
  - Append new notes to existing list (don't replace)

- [x] T119 [Dashboard] Create InfiniteScroll loader component in [`frontend/src/components/InfiniteScroll.tsx`](frontend/src/components/InfiniteScroll.tsx)
  - Use IntersectionObserver to detect scroll position
  - Show loading spinner at bottom of notes list
  - Trigger callback when user scrolls near bottom

- [x] T120 [Dashboard] Add loading state for pagination in [`Dashboard.tsx`](frontend/src/pages/Dashboard.tsx:16)
  - Track `isLoadingMore` state separately from initial load
  - Show subtle loader between notes when fetching next page
  - Prevent multiple simultaneous fetch requests

### UI/UX Polish Tasks

- [x] T121 [Dashboard] Add empty state improvement when no notes exist
  - Show helpful message with tips for new users
  - Keep "Create Your First Note" button functional

- [x] T122 [Dashboard] Add note count badge that updates dynamically
  - Display accurate count of loaded notes
  - Update when notes are added/deleted

- [x] T123 [Dashboard] Implement pull-to-refresh for mobile devices
  - Add refresh control for mobile view
  - Reload all notes when pulled down

---

## Phase 8: User Story 3 - Markdown Note Editing (Priority: P2)

**Goal**: Support Markdown formatting in note editing with real-time preview

**Independent Test**: User can type Markdown syntax in the editor and see it rendered correctly as formatted content.

### Implementation Tasks

- [x] T060 [US3] Update NoteEditor component to use react-markdown for rendering
- [x] T061 [US3] Add remark-gfm plugin support for GitHub Flavored Markdown
- [x] T062 [US3] Implement rehype-sanitize for XSS protection in rendered content
- [x] T063 [US3] Create Markdown editor state management with draft preservation
- [x] T064 [US3] Add version tracking when note content changes (for history view)

### UI/UX Tasks

- [x] T065 [US3] Implement split-view editor with live preview toggle
- [x] T066 [US3] Add Markdown toolbar with common formatting buttons

### Backend Integration Tasks

- [x] T110 [Integration] Implement auto-save mechanism with debounced API calls
- [x] T111 [Integration] Add proper error handling and user feedback for save failures

---

## Phase 9: User Story 4 - URL Preview Generation (Priority: P2)

**Goal**: Automatically generate preview cards for URLs in note content

**Independent Test**: When a note contains a URL, a preview card appears showing the title, description, and favicon of the linked page.

### Implementation Tasks

- [ ] T067 [US4] Create URL detection regex pattern for Markdown content
- [ ] T068 [US4] Implement HTTP client for fetching URL metadata (title, description, favicon)
- [ ] T069 [US4] Add 5-second timeout per URL fetch to prevent blocking
- [ ] T070 [US4] Create URL preview cache service in `src/main/kotlin/services/url_cache_service.kt`
- [ ] T071 [US4] Store preview data as JSONB in notes table (preview_data column)
- [ ] T072 [US4] Update Note entity model to include preview_data field

### UI/UX Tasks

- [ ] T073 [US4] Create React URLPreview component in `frontend/src/components/URLPreview.tsx`
- [ ] T074 [US4] Display preview card with title, description, and favicon
- [ ] T075 [US4] Handle fetch errors gracefully (show raw URL if preview fails)

---

## Phase 10: User Story 5 - File Attachments (Priority: P2)

**Goal**: Allow attaching files to notes with server-side encryption during upload/download

**Independent Test**: User can upload a file and it appears as an attachment in the note that can be downloaded.

### Implementation Tasks

- [ ] T076 [US5] Create multipart form data handler for file uploads in `src/main/kotlin/api/AttachmentsApi.kt`
- [ ] T077 [US5] Implement server-side encryption using centralized key (AES-GCM)
- [ ] T078 [US5] Add configurable max attachment size via environment variable
- [ ] T079 [US5] Create Attachment entity storage with encrypted data in database
- [ ] T080 [US5] Implement file download endpoint GET `/api/v1/notes/{id}/attachments/{attachmentId}`
- [ ] T081 [US5] Add decryption during file download using centralized encryption key

### UI/UX Tasks

- [ ] T082 [US5] Create React FileUpload component in `frontend/src/components/FileUpload.tsx`
- [ ] T083 [US5] Display uploaded files as attachment chips with filename and type icon
- [ ] T084 [US5] Implement file preview for images and PDFs

---

## Phase 11: User Story 6 - Tagging System (Priority: P3)

**Goal**: Support adding, displaying, and filtering by tags on notes

**Independent Test**: User can add tags to a note and later filter or search by those tags.

### Implementation Tasks

- [ ] T085 [US6] Create Tag entity model with user_id and name (unique per user)
- [ ] T086 [US6] Create NoteTag join table for many-to-many relationship
- [ ] T087 [US6] Add tag management API endpoints in `src/main/kotlin/api/TagsApi.kt`
- [ ] T088 [US6] Implement tag filtering query for note listing
- [ ] T089 [US6] Create Tag entity service in `src/main/kotlin/services/tag_service.kt`

### UI/UX Tasks

- [ ] T090 [US6] Create React ChipInput component in `frontend/src/components/ChipInput.tsx`
- [ ] T091 [US6] Implement chip input interface (type tag name + Enter to add)
- [ ] T092 [US6] Display tags as clickable chips on note cards
- [ ] T093 [US6] Add tag filter dropdown in dashboard for filtering notes by tag

---

## Phase 12: User Story 7 - Content Search (Priority: P3)

**Goal**: Enable search across note content, tags, and attachment filenames

**Independent Test**: User types a search query and sees results matching the text in their notes, tags, or file names.

### Implementation Tasks

- [ ] T094 [US7] Add tsvector column to notes table for full-text search
- [ ] T095 [US7] Create PostgreSQL GIN index on search_vector
- [ ] T096 [US7] Implement search API endpoint POST `/api/v1/search` in `src/main/kotlin/api/SearchApi.kt`
- [ ] T097 [US7] Add tag name matching to search query
- [ ] T098 [US7] Add attachment filename matching to search query

### UI/UX Tasks

- [ ] T099 [US7] Create React SearchBar component in `frontend/src/components/SearchBar.tsx`
- [ ] T100 [US7] Display search results with snippet highlighting
- [ ] T101 [US7] Add search query parameter to note listing API

---

## Phase 13: User Story 8 - AI-Powered Note Enhancement (Priority: P4)

**Goal**: Automatically generate summaries, titles, and tag suggestions using AI

**Independent Test**: After saving a note, an AI-generated title and summary appear, and tags are suggested based on content.

### Implementation Tasks

- [ ] T102 [US8] Create AI task queue table (ai_tasks) for async processing
- [ ] T103 [US8] Implement OpenAI API client in `src/main/kotlin/services/openai_client.kt`
- [ ] T104 [US8] Add async job scheduler to process pending AI tasks
- [ ] T105 [US8] Update Note entity with ai_title, ai_summary, ai_tags fields
- [ ] T106 [US8] Implement graceful degradation when AI service unavailable

### UI/UX Tasks

- [ ] T107 [US8] Display AI-generated title and summary in note view
- [ ] T108 [US8] Show pending status indicator while AI processing
- [ ] T109 [US8] Add tag suggestions as clickable chips

---

## Phase 14: User Story 9 - Infinite Scroll with Lazy Loading (Priority: P4)

**Goal**: Load notes progressively as user scrolls for responsive UI

**Independent Test**: User scrolls down and more notes automatically appear without manual pagination controls.

### Implementation Tasks

- [ ] T110 [US9] Implement cursor-based pagination in note listing API
- [ ] T111 [US9] Add next_cursor token to paginated responses
- [ ] T112 [US9] Create client-side cache for loaded notes in React
- [ ] T113 [US9] Implement IntersectionObserver for scroll detection

### UI/UX Tasks

- [ ] T114 [US9] Create React InfiniteScroll component in `frontend/src/components/InfiniteScroll.tsx`
- [ ] T115 [US9] Add loading spinner while fetching next page
- [ ] T116 [US9] Preserve scrolled position when new notes load

---

## Phase 15: User Story 10 - Programmatic API Access (Priority: P5)

**Goal**: Provide REST APIs for programmatic note access via API key

**Independent Test**: User provides their API key in HTTP headers and successfully creates or retrieves notes programmatically.

### Implementation Tasks

- [ ] T117 [US10] Add X-API-Key header authentication middleware
- [ ] T118 [US10] Implement API token validation against api_tokens table
- [ ] T119 [US10] Create API-only note endpoints (no session cookie required)
- [ ] T120 [US10] Add rate limiting per API key

### UI/UX Tasks

- [ ] T121 [US10] Create React APIKeyManager component in `frontend/src/components/APIKeyManager.tsx`
- [ ] T122 [US10] Display generated API keys with copy-to-clipboard functionality
- [ ] T123 [US10] Add API documentation page

---

## Phase 16: Polish & Cross-Cutting Concerns

**Purpose**: Final polish and non-functional requirements

### Performance Optimization

- [ ] T124 [P] Implement client-side caching with 30-second TTL for note lists
- [ ] T125 [P] Add database query optimization (indexes, prepared statements)
- [ ] T126 [P] Implement connection pooling for database connections

### Error Handling & Logging

- [ ] T127 [P] Add comprehensive error handling with user-friendly messages
- [ ] T128 [P] Implement structured logging with correlation IDs

### Documentation

- [ ] T129 [P] Update API documentation in `contracts/api.yaml` with all endpoints
- [ ] T130 [P] Create developer setup guide for frontend development
- [ ] T131 [P] Configure Gradle Node plugin for React build integration in `build.gradle.kts`

---

## Dependencies Graph

```
Phase 1 (Setup) ──┐
                  ├→ Phase 2 (Foundational) ──┬→ Phase 3 (US1: Quick Note Creation)
                  │                           ├→ Phase 4 (UI Design & Component Library)
                  │                           ├→ Phase 5 (Login Flow CX)
                  │                           ├→ Phase 6 (US2: OAuth Auth Backend)
                  │                           ├→ Phase 7 (Dashboard Integration) 🎯
                  │                           ├→ Phase 8 (US3: Markdown Editing)
                  │                           ├→ Phase 9 (US4: URL Preview)
                  │                           ├→ Phase 10 (US5: File Attachments)
                  │                           ├→ Phase 11 (US6: Tagging System)
                  │                           ├→ Phase 12 (US7: Content Search)
                  │                           ├→ Phase 13 (US8: AI Enhancement)
                  │                           ├→ Phase 14 (US9: Infinite Scroll)
                  │                           ├→ Phase 15 (US10: API Access)
                  └→ Phase 16 (Polish & Cross-Cutting)
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
1. **Phase 5**: Login Flow CX - Enables actual authentication UX
2. **Phase 6**: OAuth Backend - Ensures backend auth is complete
3. **Phase 7**: Dashboard Integration 🎯 - Wires up notes loading and functional note cards
4. **Phase 8**: US3 (Markdown) - Improves content formatting
5. **Phase 9-12**: Enhancement features (URL Preview, Attachments, Tagging, Search)
6. **Phase 13-15**: Advanced features (AI, Infinite Scroll, API Access)

---

## Summary

- **Total Tasks**: 143
- **Setup Phase**: 5 tasks
- **Foundational Phase**: 12 tasks (added Guice DI and server-side encryption)
- **UI Design Phase**: 25 tasks (T026-T050: design system, components, styling, accessibility)
- **Login Flow CX Phase**: 9 tasks (NEW - T051-T059)
- **Dashboard Integration Phase**: 12 tasks (NEW - T112-T123: data loading, note card functionality, infinite scroll)
- **User Story Phases**: 75 tasks (P1: 10, P2: 36, P3: 20, P4: 17, P5: 9)
- **Polish Phase**: 7 tasks (added build system integration)

**MVP Tasks**: T001-T025 (25 tasks)
