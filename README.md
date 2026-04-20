# PostOnMyWall — Social Media Multi-Publisher API

A **Java 17 / Spring Boot 3** backend that lets users publish media files
(image, video, audio) to **TikTok, Twitter/X, Instagram, and YouTube** in one
click, with S3 file storage, full publish logging, and CRON-based scheduling.

---

## Tech Stack

| Layer        | Technology                        |
|-------------|-----------------------------------|
| Language     | Java 17                           |
| Framework    | Spring Boot 3.2                   |
| Security     | Spring Security + JWT (jjwt 0.12) |
| Database     | PostgreSQL 16 + Flyway migrations |
| File Storage | AWS S3 (AWS SDK v2)               |
| HTTP Client  | Spring WebFlux WebClient          |
| Docs         | SpringDoc OpenAPI (Swagger UI)    |
| Build        | Maven                             |
| Tests        | JUnit 5 + Mockito + H2            |
| Container    | Docker + Docker Compose           |

---

## Quick Start

### 1. Prerequisites
- JDK 17+
- Docker & Docker Compose
- AWS S3 bucket (or LocalStack for local dev)

### 2. Configure environment
```bash
cp .env.example .env
# Edit .env and fill in AWS keys, JWT secret, and social API credentials
```

### 3. Run with Docker Compose
```bash
docker-compose up -d
```

### 4. Run locally (with existing Postgres)
```bash
# Start only Postgres
docker-compose up -d postgres

# Run the app
./mvnw spring-boot:run
```

### 5. Swagger UI
```
http://localhost:8080/swagger-ui.html
```

---

## API Reference

### Authentication — `/api/v1/auth`

| Method | Path        | Description              | Auth required |
|--------|-------------|--------------------------|---------------|
| POST   | `/register` | Register a new user      | No            |
| POST   | `/login`    | Login, receive JWT token | No            |

All other endpoints require: `Authorization: Bearer <token>`

---

### Social Accounts — `/api/v1/accounts`

| Method | Path          | Description             |
|--------|---------------|-------------------------|
| POST   | `/`           | Link a social account   |
| GET    | `/`           | List linked accounts    |
| DELETE | `/{accountId}`| Unlink a social account |

**Link account request body:**
```json
{
  "platform": "TWITTER",
  "accountId": "@mouad_toto",
  "accessToken": "your_oauth_token",
  "tokenSecret": "optional_for_oauth1"
}
```
Supported platforms: `TIKTOK`, `TWITTER`, `INSTAGRAM`, `YOUTUBE`

---

### Media Files — `/api/v1/files`

| Method | Path        | Description                                |
|--------|-------------|--------------------------------------------|
| POST   | `/`         | Upload a file to S3 (multipart/form-data)  |
| GET    | `/`         | List files (paginated)                     |
| GET    | `/{fileId}` | Get file + fresh pre-signed S3 URL         |
| DELETE | `/{fileId}` | Delete file from S3 and mark inactive in DB|

Supported file types: JPEG, PNG, GIF, WebP, MP4, MOV, AVI, WebM, MP3, WAV, OGG, AAC

---

### Publish — `/api/v1/publish`

| Method | Path       | Description                                   |
|--------|------------|-----------------------------------------------|
| POST   | `/`        | Publish a file to a social platform           |
| DELETE | `/{logId}` | Remove a published post from platform         |
| GET    | `/`        | List all publish logs (paginated)             |
| GET    | `/{logId}` | Get a single publish log                      |

**Publish request body:**
```json
{
  "mediaFileId": "uuid-of-uploaded-file",
  "socialAccountId": "uuid-of-linked-account",
  "title": "Check out my new ad!",
  "description": "Optional extended description"
}
```

Publish status values: `PENDING` → `PUBLISHED` | `FAILED`, then `REMOVED`

---

### Scheduler — `/api/v1/schedules`

| Method | Path       | Description                            |
|--------|------------|----------------------------------------|
| POST   | `/`        | Create a DAILY or WEEKLY schedule      |
| GET    | `/`        | List all schedules for current user    |
| DELETE | `/{jobId}` | Cancel (deactivate) a schedule         |

**Create schedule request body:**
```json
{
  "mediaFileId": "uuid",
  "socialAccountId": "uuid",
  "title": "My daily ad post",
  "description": "Optional",
  "frequency": "DAILY"
}
```
Frequency values: `DAILY` (runs at 09:00 every day), `WEEKLY` (runs at 09:00 every Monday)

---

## Example: Full Publish Flow

```bash
# 1. Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"mouad","email":"mouad@test.com","password":"password123"}'

# 2. Login — grab the token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"mouad","password":"password123"}' | jq -r '.data.token')

# 3. Link a Twitter account
ACCOUNT_ID=$(curl -s -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"platform":"TWITTER","accountId":"@mouad_toto","accessToken":"your_token"}' \
  | jq -r '.data.id')

# 4. Upload a file
FILE_ID=$(curl -s -X POST http://localhost:8080/api/v1/files \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/video.mp4" | jq -r '.data.id')

# 5. Publish in one click
curl -X POST http://localhost:8080/api/v1/publish \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"mediaFileId\":\"$FILE_ID\",\"socialAccountId\":\"$ACCOUNT_ID\",\"title\":\"My ad!\"}"

# 6. Schedule daily auto-publish
curl -X POST http://localhost:8080/api/v1/schedules \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"mediaFileId\":\"$FILE_ID\",\"socialAccountId\":\"$ACCOUNT_ID\",\"title\":\"Daily ad\",\"frequency\":\"DAILY\"}"
```

---

## Project Structure

```
src/main/java/com/postonmywall/
├── PostOnMyWallApplication.java
├── auth/
│   ├── User.java
│   ├── UserRepository.java
│   ├── CustomUserDetailsService.java
│   ├── AuthService.java
│   ├── AuthController.java
│   └── RegisterRequest / LoginRequest / AuthResponse / UserResponse
├── account/
│   ├── SocialAccount.java
│   ├── SocialAccountRepository.java
│   ├── SocialAccountService.java
│   ├── SocialAccountController.java
│   └── LinkAccountRequest / SocialAccountResponse
├── file/
│   ├── MediaFile.java
│   ├── MediaFileRepository.java
│   ├── S3Service.java
│   ├── MediaFileService.java
│   ├── MediaFileController.java
│   └── MediaFileResponse
├── publish/
│   ├── SocialMediaAdapter.java        ← interface
│   ├── SocialAdapters.java            ← TikTok, Twitter, Instagram, YouTube
│   ├── SocialAdapterRegistry.java
│   ├── PublishLog.java
│   ├── PublishLogRepository.java
│   ├── PublishService.java
│   ├── PublishController.java
│   └── PublishRequest / PublishResponse
├── scheduler/
│   ├── ScheduledPublish.java
│   ├── ScheduledPublishRepository.java
│   ├── ScheduledPublishService.java
│   ├── PublishScheduler.java          ← @Scheduled CRON
│   ├── SchedulerController.java
│   └── CreateScheduleRequest / ScheduledPublishResponse
├── config/
│   ├── AppConfig.java                 ← S3, WebClient, OpenAPI, JPA auditing
│   ├── SecurityConfig.java
│   ├── JwtTokenProvider.java
│   └── JwtAuthenticationFilter.java
├── common/
│   ├── ApiResponse.java
│   ├── Platform.java / MediaType.java / FileStatus.java
│   ├── PublishStatus.java / Frequency.java
└── exception/
    └── GlobalExceptionHandler.java    ← all domain exceptions + @RestControllerAdvice
```

---

## Social API Limitations

| Platform  | Publish | Remove | Notes                                         |
|-----------|---------|--------|-----------------------------------------------|
| Twitter/X | ✅      | ✅     | OAuth2 bearer token; v1 media needs API key   |
| YouTube   | ✅      | ✅     | Requires OAuth2 consent; defaults to private  |
| TikTok    | ✅      | ✅     | Requires approved developer account           |
| Instagram | ✅      | ⚠️    | Graph API deletion not supported for most apps|

All API keys are stored in `application.properties` / `.env` — never hardcoded.

---

## Running Tests

```bash
./mvnw test
```
