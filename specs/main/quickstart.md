# Quickstart Guide: Notes App with AI Integration

**Branch**: `bubbles-notes` | **Date**: 2026-03-18

## Prerequisites

- Java 26 or higher
- Gradle 8.5 or higher (included via wrapper)
- PostgreSQL 14+ (for production) or H2 (embedded, for testing)

## Development Setup

### 1. Clone the Repository

```bash
git clone <repository-url>
cd BubblesNotes
```

### 2. Configure Environment Variables

Create a `.env` file in the project root:

```bash
# Database Configuration
DATABASE_URL=jdbc:postgresql://localhost:5432/notesapp
DATABASE_USER=your_username
DATABASE_PASSWORD=your_password
PRODUCTION=false  # Set to true for PostgreSQL, false for H2 (embedded)

# Google OAuth Configuration
GOOGLE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-google-client-secret
GOOGLE_REDIRECT_URI=http://localhost:8080/auth/google/callback

# AI Service Configuration (optional)
OPENAI_API_KEY=sk-...  # For AI-powered note enhancement
AI_SERVICE_URL=https://api.openai.com/v1/chat/completions

# File Upload Configuration
MAX_ATTACHMENT_SIZE=10485760  # 10MB in bytes
```

### 3. Run the Application

#### Development Mode (with H2 embedded database)

```bash
./gradlew run
```

The application will start on `http://localhost:8080` with an embedded H2 database.

#### Production Mode (with PostgreSQL)

1. Create the database:
   ```sql
   CREATE DATABASE notesapp;
   ```

2. Set `PRODUCTION=true` in `.env`

3. Run:
   ```bash
   ./gradlew run
   ```

### 4. Access the Application

Open your browser and navigate to:

```
http://localhost:8080
```

You should see the notes app landing page with a "Sign in with Google" button.

### 5. Building the React Frontend

The React frontend is built as part of the Gradle build process. When you run `./gradlew build` or `./gradlew jar`, the following happens:

1. **React Compilation**: The build system runs `npm run build` in the `frontend/` directory
2. **Asset Bundling**: Compiled assets from `frontend/build/` are copied to Ktor's static resources (`src/main/resources/static/`)
3. **JAR Creation**: The final JAR contains both Kotlin backend and React frontend

**To build a fat JAR with all dependencies and frontend assets**:
```bash
./gradlew buildFatJar
```

The resulting JAR will be located at `build/libs/BubblesNotes-0.0.1-all.jar` and can be run directly:
```bash
java -jar build/libs/BubblesNotes-0.0.1-all.jar
```

## Testing

### Run Unit Tests

```bash
./gradlew test
```

### Run Integration Tests

```bash
./gradlew integrationTest
```

### Test with Embedded Database

The application uses H2 database by default when `PRODUCTION=false`. No additional setup required.

## API Quickstart

### Create a Note (via API)

```bash
# 1. Get your OAuth token first (via Google login flow)
# Then create a note:

curl -X POST http://localhost:8080/api/v1/notes \
  -H "Authorization: Bearer YOUR_OAUTH_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "My First Note",
    "content": "# Hello World\nThis is my first note with **Markdown** support.",
    "tags": ["welcome", "first-note"]
  }'
```

### List Notes

```bash
curl http://localhost:8080/api/v1/notes \
  -H "Authorization: Bearer YOUR_OAUTH_TOKEN"
```

### Search Notes

```bash
curl -X POST http://localhost:8080/api/v1/search \
  -H "Authorization: Bearer YOUR_OAUTH_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "hello",
    "limit": 20
  }'
```

## Database Migrations

The application uses Flyway for database migrations. Migrations are located in `src/main/resources/db/migration/`.

### Run Migrations Manually

```bash
./gradlew flywayMigrate
```

### Reset Database (Development Only)

```sql
-- For PostgreSQL
DROP DATABASE notesapp;
CREATE DATABASE notesapp;

-- For H2 (embedded)
-- Just restart the application - it will recreate tables
```

## Configuration Reference

| Property | Description | Default |
|----------|-------------|---------|
| `ktor.deployment.port` | Server port | 8080 |
| `database.url` | PostgreSQL connection URL | jdbc:postgresql://localhost/default |
| `database.user` | Database username | username |
| `database.password` | Database password | password |
| `production` | Use PostgreSQL (true) or H2 (false) | false |
| `google.clientId` | Google OAuth client ID | (required) |
| `google.clientSecret` | Google OAuth client secret | (required) |
| `openai.apiKey` | OpenAI API key for AI enhancement | (optional) |
| `maxAttachmentSize` | Maximum file upload size in bytes | 10485760 (10MB) |

## Troubleshooting

### Port Already in Use

```bash
# Find process using port 8080
lsof -i :8080

# Or change the port in application.yaml
```

### Database Connection Failed

1. Verify PostgreSQL is running:
   ```bash
   psql -U your_username -d notesapp -c "SELECT 1"
   ```

2. Check connection parameters in `.env`

3. Ensure database exists:
   ```sql
   CREATE DATABASE notesapp;
   ```

### Google OAuth Not Working

1. Verify redirect URI matches exactly:
   ```
   http://localhost:8080/auth/google/callback
   ```

2. Check OAuth consent screen configuration in Google Cloud Console

3. Ensure API credentials are correct in `.env`

## Next Steps

- Read the [Data Model](data-model.md) for entity details
- Review the [API Contract](contracts/api.yaml) for endpoint specifications
- See [Research](research.md) for technical decisions and implementation details
