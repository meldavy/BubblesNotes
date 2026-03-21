# Code Sanity Improvement Plan

This document outlines the systematic approach to improve code quality and maintainability in the BubblesNotes backend project.

## Objectives

1. **Replace Fully Qualified Names with Imports** - Use proper imports instead of inline fully qualified class names (unless there are naming conflicts)
2. **Clean Up Unused Methods** - Remove methods that are not called anywhere in the codebase
3. **Clean Up Unused Properties** - Remove properties that are declared but never used

---

## Phase 1: Replace Fully Qualified Names with Imports

### Approach
1. Scan all Kotlin source files for inline fully qualified class names
2. Identify files with naming conflicts that require fully qualified names
3. Add proper imports for files without conflicts
4. Replace fully qualified names with simple names

### Files to Scan
- `src/main/kotlin/com/mel/bubblenotes/Application.kt`
- `src/main/kotlin/com/mel/bubblenotes/Databases.kt`
- `src/main/kotlin/com/mel/bubblenotes/GuiceModules.kt`
- `src/main/kotlin/com/mel/bubblenotes/Monitoring.kt`
- `src/main/kotlin/com/mel/bubblenotes/Routing.kt`
- `src/main/kotlin/com/mel/bubblenotes/Security.kt`
- `src/main/kotlin/com/mel/bubblenotes/Serialization.kt`
- `src/main/kotlin/com/mel/bubblenotes/Sockets.kt`
- `src/main/kotlin/com/mel/bubblenotes/api/NotesApi.kt`
- `src/main/kotlin/com/mel/bubblenotes/api/SearchApi.kt`
- `src/main/kotlin/com/mel/bubblenotes/api/TagsApi.kt`
- `src/main/kotlin/com/mel/bubblenotes/models/*.kt`
- `src/main/kotlin/com/mel/bubblenotes/repositories/*.kt`
- `src/main/kotlin/com/mel/bubblenotes/serializers/*.kt`
- `src/main/kotlin/com/mel/bubblenotes/services/*.kt`

### Verification
- Compile the project with `./gradlew compileKotlin`
- Ensure no naming conflicts or import errors

---

## Phase 2: Clean Up Unused Methods

### Approach
1. For each method in the codebase, search for its usage across all files
2. Identify truly unused methods (not called anywhere, including tests)
3. Remove unused methods one file at a time
4. Compile and test after each removal

### Priority Files
1. **Services** - Often accumulate legacy methods
   - `OpenAIClient.kt`
   - `URLPreviewService.kt`
   - `AIEnhancementService.kt`
   - `TagService.kt`
   - `ApiKeyService.kt`
   - `DatabaseService.kt`
   - `EncryptionService.kt`
   - `SessionStorage.kt`

2. **Repositories** - Check for deprecated query methods
   - `NoteRepository.kt`
   - `AITaskRepository.kt`
   - `AttachmentRepository.kt`
   - `NoteTagRepository.kt`
   - `TagRepository.kt`
   - `UserRepository.kt`

3. **API Layer** - Check for unused endpoint helpers
   - `NotesApi.kt`
   - `SearchApi.kt`
   - `TagsApi.kt`

### Verification
- Compile the project with `./gradlew compileKotlin`
- Run tests with `./gradlew test`

---

## Phase 3: Clean Up Unused Properties

### Approach
1. For each property in the codebase, search for its usage
2. Identify truly unused properties (not read or written anywhere)
3. Remove unused properties one file at a time
4. Compile and test after each removal

### Priority Files
1. **Models** - Check for deprecated fields
   - `Note.kt`
   - `User.kt`
   - `AITask.kt`
   - `Attachment.kt`
   - `NoteTag.kt`
   - `Tag.kt`
   - `Version.kt`
   - `APIKey.kt`

2. **Services** - Check for unused configuration properties
   - All service files

3. **API Layer** - Check for unused dependency injection properties
   - `NotesApi.kt`
   - `SearchApi.kt`
   - `TagsApi.kt`

### Verification
- Compile the project with `./gradlew compileKotlin`
- Run tests with `./gradlew test`

---

## Phase 4: Final Verification

### Commands
```bash
# Compile the project
./gradlew compileKotlin

# Run all tests
./gradlew test

# Format code (optional, for consistency)
./gradlew ktlintFormat
```

### Success Criteria
- Project compiles without errors
- All tests pass
- No unused imports or properties warnings (if using IDE inspection)

---

## Tools and Commands

### Search Commands
- Search for fully qualified names: `com.mel.bubblenotes\.`
- Search for method usage: `methodName(`
- Search for property usage: `propertyName`

### Build Commands
```bash
# Compile
./gradlew compileKotlin

# Run tests
./gradlew test

# Run specific test
./gradlew test --tests "com.mel.bubblenotes.repositories.NoteRepositoryTest"

# Format code
./gradlew ktlintFormat

# Build production artifact
./gradlew build
```

---

## Notes

- **Testability Constitution Compliance**: Ensure all changes maintain testability requirements:
  - Repository classes must remain `open`
  - Services must use constructor injection
  - No database-specific SQL that breaks H2 compatibility

- **Incremental Changes**: Make changes one file at a time to minimize risk and enable quick rollback if needed

- **Documentation**: Update this document with findings and changes made during each phase
