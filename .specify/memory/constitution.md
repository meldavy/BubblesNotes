<!--
SYNC IMPACT REPORT:
- Version change: N/A → 1.0.0 (initial constitution)
- Added principles:
  * I. Security (Priority #1): User-domain data isolation with OAuth-derived encryption keys
  * II. Testing (Priority #2): Mockable, decoupled architecture with JUnit/MockK and Guice DI
- Sections added: Core Principles (2 principles), Governance
- Templates requiring updates:
  * .specify/templates/plan-template.md - Constitution Check section aligns with new principles ✅
  * .specify/templates/spec-template.md - Requirements section should reference Security/Testing ✅
  * .specify/templates/tasks-template.md - Task categorization reflects testing discipline ✅
- Follow-up TODOs: None (all placeholders resolved)
-->

# BubblesNotes Constitution

## Core Principles

### I. Security (Priority #1)

All user-domain data MUST be accessible only by the owning user. Admin personnel with direct host or database access SHALL NOT be able to decrypt or view raw contents of user-domain data.

**Implementation Requirements:**
- User authentication via Google OAuth exclusively
- Encryption keys derived deterministically from OAuth identity tokens (consistent across sessions for same user)
- Encryption keys MUST NEVER be stored on the server side
- Field-level or end-to-end encryption for all user-domain data at rest
- Data isolation enforced at application layer before database persistence

**Rationale:** This principle ensures zero-trust architecture where the service provider cannot access user data, protecting privacy even in cases of compromise or administrative overreach.

### II. Testing (Priority #2)

All features and code MUST be designed and implemented to be mockable and testable, with no strict dependencies.

**Implementation Requirements:**
- Dependency Injection via Google Guice for all external dependencies
- Testing stack: JUnit 5 for test framework, MockK for Kotlin mocking
- All services must accept dependencies via constructor injection
- No direct instantiation of external dependencies (database, HTTP clients, etc.) within business logic
- Unit tests MUST be able to run without external service connections

**Rationale:** Testable architecture enables rapid feedback, prevents regression bugs, and ensures code quality through automated verification. Decoupling allows parallel development and easier maintenance.

## Governance

This constitution supersedes all other development practices and guidelines within the BubblesNotes project.

**Amendment Procedure:**
- Changes require documentation of rationale in Sync Impact Report
- Version must follow semantic versioning (MAJOR.MINOR.PATCH)
- All dependent templates must be updated to reflect changes

**Versioning Policy:**
- MAJOR: Backward incompatible principle additions, removals, or redefinitions
- MINOR: New principle added or existing principle materially expanded
- PATCH: Clarifications, wording improvements, typo fixes

**Compliance Review:**
- All pull requests must verify constitution compliance
- Architecture decisions must be justified against these principles
- Security and Testing principles are NON-NEGOTIABLE and take precedence over feature velocity

**Version**: 1.0.0 | **Ratified**: 2026-03-17 | **Last Amended**: 2026-03-17
