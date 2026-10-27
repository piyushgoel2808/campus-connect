# 📂 Codebase Directory & File Reference — Campus Connect (GradLink)

> **Live Deployment:** [https://campus-connect-live.onrender.com](https://campus-connect-live.onrender.com)

This document serves as a complete map and reference guide to every directory, Java class, REST/STOMP endpoint, frontend ES6 module, HTML template, CSS stylesheet, and configuration file in the **Campus Connect** repository.

---

## 1. Complete Repository Tree

```text
campus-connect/
├── Dockerfile                                 # Multi-stage Maven + Eclipse Temurin JRE 17 container build
├── docker-compose.yml                         # Isolated MySQL 8.0 + Spring Boot app container stack
├── pom.xml                                    # Maven dependencies & Spring Boot 3.2.5 build config
├── README.md                                  # Project overview, live link, quickstart, and demo credentials
├── PROJECT_ANALYSIS.md                        # Architectural analysis and engineering notes
├── SPRINGBOOT_INTERVIEW_GUIDE.md              # Spring Boot technical Q&A guide based on this project
├── docs/                                      # Detailed technical documentation suite
│   ├── README.md                              # Documentation hub & index
│   ├── architecture.md                        # System, security, WebSocket, and SPA architecture
│   ├── codebase-directory.md                  # File-by-file codebase directory & API reference (this file)
│   ├── dataflow.md                            # End-to-end sequence diagrams & request lifecycles
│   ├── database-schema.md                     # ER diagram & table-by-table column specifications
│   └── features.md                            # Complete product features & RBAC matrix
├── src/
│   ├── main/
│   │   ├── java/com/bvicam/campusconnect/
│   │   │   ├── CampusConnectApplication.java  # @SpringBootApplication & @EnableAsync entry point
│   │   │   ├── config/                        # Security, JWT filter, WebSocket, MVC, & DataSeeder
│   │   │   │   ├── DataSeeder.java
│   │   │   │   ├── JwtAuthenticationFilter.java
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   ├── WebMvcConfig.java
│   │   │   │   └── WebSocketConfig.java
│   │   │   ├── controller/                    # REST (/api/*) and STOMP (@MessageMapping) controllers
│   │   │   │   ├── AdminController.java
│   │   │   │   ├── AuthController.java
│   │   │   │   ├── BotController.java
│   │   │   │   ├── ChatController.java
│   │   │   │   ├── EventController.java
│   │   │   │   ├── FeedbackController.java
│   │   │   │   ├── JobController.java
│   │   │   │   ├── NotificationController.java
│   │   │   │   ├── PostController.java
│   │   │   │   └── UserController.java
│   │   │   ├── dto/                           # Request & response Data Transfer Objects
│   │   │   │   ├── AuthResponse.java
│   │   │   │   ├── ChatMessage.java
│   │   │   │   ├── EventDTO.java
│   │   │   │   ├── LoginRequest.java
│   │   │   │   ├── NotificationDto.java
│   │   │   │   ├── RegisterRequest.java
│   │   │   │   ├── TypingMessage.java
│   │   │   │   └── UserProfileDto.java
│   │   │   ├── entity/                        # JPA @Entity domain classes & enums
│   │   │   │   ├── ChatMessage.java
│   │   │   │   ├── Comment.java
│   │   │   │   ├── Department.java
│   │   │   │   ├── Event.java
│   │   │   │   ├── Feedback.java
│   │   │   │   ├── Job.java
│   │   │   │   ├── Notification.java
│   │   │   │   ├── NotificationType.java
│   │   │   │   ├── Post.java
│   │   │   │   ├── PrivateMessage.java
│   │   │   │   ├── Role.java
│   │   │   │   └── User.java
│   │   │   ├── repository/                    # Spring Data JPA repositories
│   │   │   │   ├── CommentRepository.java
│   │   │   │   ├── DepartmentRepository.java
│   │   │   │   ├── EventRepository.java
│   │   │   │   ├── FeedbackRepository.java
│   │   │   │   ├── JobRepository.java
│   │   │   │   ├── NotificationRepository.java
│   │   │   │   ├── PostRepository.java
│   │   │   │   ├── PrivateMessageRepository.java
│   │   │   │   └── UserRepository.java
│   │   │   ├── service/                       # Transactional business logic, moderation & async email
│   │   │   │   ├── CustomUserDetailsService.java
│   │   │   │   ├── EmailService.java
│   │   │   │   ├── EventService.java
│   │   │   │   ├── NotificationService.java
│   │   │   │   ├── PostService.java
│   │   │   │   ├── ProfanityFilterService.java
│   │   │   │   └── UserModerationService.java
│   │   │   └── util/
│   │   │       └── JwtUtil.java               # HMAC-SHA256 JWT generation, parsing & validation
│   │   └── resources/
│   │       ├── application.properties         # Database, JPA, SMTP, and multipart upload config
│   │       └── static/                        # Frontend static web application
│   │           ├── index.html                 # Public landing page
│   │           ├── login.html                 # Split-screen authentication page
│   │           ├── dashboard.html             # Authenticated SPA shell
│   │           ├── components/                # Dynamically injected tab views
│   │           │   ├── admin.html
│   │           │   ├── directory.html
│   │           │   ├── events.html
│   │           │   ├── jobs.html
│   │           │   ├── messages.html
│   │           │   ├── profile.html
│   │           │   └── wall.html
│   │           ├── css/                       # Modular CSS design system
│   │           │   ├── tokens.css
│   │           │   ├── shell.css
│   │           │   ├── components.css
│   │           │   ├── auth.css
│   │           │   └── landing.css
│   │           └── js/                        # Vanilla ES6 modules
│   │               ├── app.js
│   │               ├── dashboard-main.js
│   │               ├── utils/
│   │               │   └── api.js
│   │               └── modules/
│   │                   ├── admin.js
│   │                   ├── bot.js
│   │                   ├── chat.js
│   │                   ├── directory.js
│   │                   ├── events.js
│   │                   ├── feedback.js
│   │                   ├── jobs.js
│   │                   ├── notifications.js
│   │                   ├── profile.js
│   │                   └── wall.js
│   └── test/                                  # JUnit 5 & Mockito test suite
│       ├── java/com/bvicam/campusconnect/
│       │   ├── CampusConnectApplicationTests.java
│       │   └── controller/
│       │       ├── AuthControllerTest.java
│       │       └── ChatControllerTest.java
│       └── resources/
│           └── application-test.properties    # H2 in-memory database test configuration
```

---

## 2. Backend File Reference (`src/main/java/com/bvicam/campusconnect`)

### 2.1 Entry Point, Utilities & Configuration (`config/` & `util/`)

| File | Key Classes / Beans | Purpose & Key Details |
| :--- | :--- | :--- |
| [`CampusConnectApplication.java`](../src/main/java/com/bvicam/campusconnect/CampusConnectApplication.java) | `CampusConnectApplication` | Main Spring Boot entry point (`@SpringBootApplication`). Enables background thread execution via `@EnableAsync` (used by `EmailService`). |
| [`util/JwtUtil.java`](../src/main/java/com/bvicam/campusconnect/util/JwtUtil.java) | `JwtUtil` | Generates and validates HMAC-SHA256 JWT tokens with a 10-hour expiration window. Uses `app.jwt.secret` from properties. |
| [`config/JwtAuthenticationFilter.java`](../src/main/java/com/bvicam/campusconnect/config/JwtAuthenticationFilter.java) | `JwtAuthenticationFilter` | Extends `OncePerRequestFilter`. Extracts the `Authorization: Bearer <token>` header, validates the token via `JwtUtil`, and populates `SecurityContextHolder`. |
| [`config/SecurityConfig.java`](../src/main/java/com/bvicam/campusconnect/config/SecurityConfig.java) | `SecurityConfig` | Configures stateless Spring Security (`SessionCreationPolicy.STATELESS`), permissive CORS, `BCryptPasswordEncoder`, `AuthenticationManager`, and route permissions. |
| [`config/WebSocketConfig.java`](../src/main/java/com/bvicam/campusconnect/config/WebSocketConfig.java) | `WebSocketConfig` | Registers the `/ws` SockJS STOMP endpoint and configures the in-memory message broker (`/topic`, `/queue`, `/user`) and `/app` application destination prefix. |
| [`config/WebMvcConfig.java`](../src/main/java/com/bvicam/campusconnect/config/WebMvcConfig.java) | `WebMvcConfig` | Maps HTTP requests for `/uploads/**` to the local `uploads/` filesystem directory so uploaded post photos are served directly. |
| [`config/DataSeeder.java`](../src/main/java/com/bvicam/campusconnect/config/DataSeeder.java) | `DataSeeder` | `CommandLineRunner` that idempotently seeds default departments (`MCA`, `BJMC`), demo accounts (`admin@bvicam.in`, `alumni@bvicam.in`, `student@bvicam.in`), a sample job, and a welcome wall post on first startup. |

---

### 2.2 REST & WebSocket Controllers (`controller/`)

| Controller | Base Path | Endpoints & Operations |
| :--- | :--- | :--- |
| [`AuthController.java`](../src/main/java/com/bvicam/campusconnect/controller/AuthController.java) | `/api/auth` | • `POST /api/auth/register` — Registers a new user with BCrypt password hashing.<br>• `POST /api/auth/login` — Authenticates credentials and returns `AuthResponse` (JWT, id, name, role).<br>• `POST /api/auth/change-password` — Updates user password and sets `passwordChanged = true`. |
| [`AdminController.java`](../src/main/java/com/bvicam/campusconnect/controller/AdminController.java) | `/api/admin` | • `GET /api/admin/users` — Lists all users.<br>• `POST /api/admin/users` — Creates an individual user (with department resolution & default/custom password).<br>• `PUT /api/admin/users/{id}` — Updates any user's identity, academic, and professional fields.<br>• `DELETE /api/admin/delete-user/{id}` — Safely removes a user after cleaning up event RSVPs, comments, posts, jobs, and feedback.<br>• `PUT /api/admin/users/{id}/unflag` — Clears moderation flags (`isFlagged = false`, `flagCount = 0`).<br>• `PUT /api/admin/users/{id}/reset-password` — Resets user password to `Bvicam@2025`.<br>• `POST /api/admin/broadcast` — Triggers async email broadcast to all users.<br>• `DELETE /api/admin/delete-post/{id}` — Deletes a wall post.<br>• `POST /api/admin/bulk-upload` — Parses `.xlsx` (Apache POI) or `.csv` files to bulk-onboard accounts. |
| [`UserController.java`](../src/main/java/com/bvicam/campusconnect/controller/UserController.java) | `/api/users` | • `GET /api/users/me` — Returns current user's `UserProfileDto`.<br>• `GET /api/users` — Returns all users for directory/admin tables.<br>• `GET /api/users/search?role=&batch=&q=` — Multi-criteria directory search returning `List<UserProfileDto>`.<br>• `PUT /api/users/profile` — Updates logged-in user's profile and notification preferences. |
| [`PostController.java`](../src/main/java/com/bvicam/campusconnect/controller/PostController.java) | `/api/posts` | • `GET /api/posts` — Lists wall posts (`isPinned DESC, createdAt DESC`).<br>• `POST /api/posts/upload` — Uploads post photo (`png`, `jpg`, `gif`, `webp` up to 5 MB) to `/uploads/`.<br>• `POST /api/posts` — Creates a post (screened by `ProfanityFilterService`).<br>• `DELETE /api/posts/{id}` — Deletes a post (Author or `ADMIN`).<br>• `PUT /api/posts/{id}/like` — Increments post like counter.<br>• `PUT /api/posts/{id}/pin` — Toggles pinned announcement status (`ADMIN` only).<br>• `GET /api/posts/{id}/comments` — Lists top-level comments with nested replies.<br>• `POST /api/posts/{id}/comments` — Adds a top-level comment or nested reply (with `parentCommentId`).<br>• `DELETE /api/posts/comments/{commentId}` — Deletes a comment and its replies (Author or `ADMIN`). |
| [`EventController.java`](../src/main/java/com/bvicam/campusconnect/controller/EventController.java) | `/api/events` | • `GET /api/events` — Returns `List<EventDTO>` with computed `attending` flag and `participantCount`.<br>• `POST /api/events` — Creates an event and dispatches notifications to all users.<br>• `PUT /api/events/{id}` — Updates event title, description, location, and date/time.<br>• `POST /api/events/{id}/rsvp` — Toggles current user's attendance in `event_participants`.<br>• `GET /api/events/{id}/participants` — Returns `Set<User>` attending the event.<br>• `DELETE /api/events/{id}` — Deletes an event. |
| [`JobController.java`](../src/main/java/com/bvicam/campusconnect/controller/JobController.java) | `/api/jobs` | • `GET /api/jobs` — Lists all job/referral postings newest-first.<br>• `POST /api/jobs` — Publishes a job/referral (`ADMIN` or `ALUMNI`) and notifies `STUDENT` users.<br>• `PUT /api/jobs/{id}` — Edits an existing job/referral (`ADMIN` or original poster).<br>• `DELETE /api/jobs/{id}` — Deletes a job/referral (`ADMIN` or original poster). |
| [`ChatController.java`](../src/main/java/com/bvicam/campusconnect/controller/ChatController.java) | STOMP `/app`<br>REST `/api/messages` | • `@MessageMapping("/chat.sendMessage")` — Broadcasts public chat message to `/topic/public`.<br>• `@MessageMapping("/chat.addUser")` — Broadcasts user join event to `/topic/public`.<br>• `@MessageMapping("/chat.private")` — Saves 1-on-1 `PrivateMessage`, sends to `/user/{email}/queue/messages`, and dispatches notification.<br>• `@MessageMapping("/chat.typing")` — Forwards typing indicator to `/topic/typing/{receiverEmail}`.<br>• `GET /api/messages/history?otherUser=` — Returns chronological 1-on-1 chat history.<br>• `GET /api/messages/partners` — Returns `User` profiles of recent conversation partners. |
| [`NotificationController.java`](../src/main/java/com/bvicam/campusconnect/controller/NotificationController.java) | `/api/notifications` | • `GET /api/notifications` — Returns top 10 recent notifications for current user.<br>• `GET /api/notifications/unread-count` — Returns `{ "unreadCount": N }`.<br>• `PUT /api/notifications/{id}/read` — Marks a single notification as read.<br>• `PUT /api/notifications/read-all` — Marks all notifications as read. |
| [`FeedbackController.java`](../src/main/java/com/bvicam/campusconnect/controller/FeedbackController.java) | `/api/feedback` | • `POST /api/feedback` — Submits a 1–5 star rating and comment.<br>• `GET /api/feedback` — Returns all submitted feedback newest-first (`ADMIN` review). |
| [`AdminKnowledgeController.java`](../src/main/java/com/bvicam/campusconnect/controller/AdminKnowledgeController.java) | `/api/admin/knowledge` | • `POST /api/admin/knowledge/upload` — Ingests PDF/TXT notices into vector knowledge base via Apache PDFBox.<br>• `POST /api/admin/knowledge/scrape-url` — Crawls official BVICAM web pages via Jsoup.<br>• `GET /api/admin/knowledge/documents` — Lists all indexed documents and source URLs.<br>• `DELETE /api/admin/knowledge/documents/{id}` — Cascading delete for documents and vector chunks. |
| [`BotController.java`](../src/main/java/com/bvicam/campusconnect/controller/BotController.java) | `/api/bot` | • `POST /api/bot/ask` — Conversational GradBot assistant powered by local Ollama (`gemma4:e4b`), hybrid RAG vector search, live MySQL event/job/batch context, and citations. |

---

### 2.3 Services (`service/`)

| Service File | Key Methods | Responsibility |
| :--- | :--- | :--- |
| [`CustomUserDetailsService.java`](../src/main/java/com/bvicam/campusconnect/service/CustomUserDetailsService.java) | `loadUserByUsername(email)` | Bridges JPA `User` entity with Spring Security's `UserDetails` (`ROLE_STUDENT`, `ROLE_ALUMNI`, `ROLE_ADMIN`). |
| [`PostService.java`](../src/main/java/com/bvicam/campusconnect/service/PostService.java) | `getAllPosts`, `createPost`, `likePost`, `togglePin`, `deletePost`, `addComment`, `getCommentsForPost`, `deleteComment` | Manages Campus Wall posts and nested comments. Screens post and comment text via `ProfanityFilterService` and flags offending users via `UserModerationService`. |
| [`ProfanityFilterService.java`](../src/main/java/com/bvicam/campusconnect/service/ProfanityFilterService.java) | `containsProfanity(text)` | Bilingual (English + Hindi/Hinglish) profanity detector with leetspeak normalization (`@->a`, `$->s`, `0->o`), repeated-character collapsing, and suffix stemming. |
| [`UserModerationService.java`](../src/main/java/com/bvicam/campusconnect/service/UserModerationService.java) | `flagUser(userId)`, `unflagUser(userId)` | Uses `@Transactional(propagation = Propagation.REQUIRES_NEW)` so `isFlagged = true` and `flagCount++` are committed to MySQL even when the offending post/comment transaction rolls back. |
| [`NotificationService.java`](../src/main/java/com/bvicam/campusconnect/service/NotificationService.java) | `createAndDispatch`, `getRecentNotifications`, `getUnreadCount`, `markAsRead`, `markAllAsRead` | Checks user notification preferences (`notifyMessages`, `notifyEvents`, `notifyJobs`), persists `Notification` records, and pushes live STOMP payloads to `/user/{email}/queue/notifications`. |
| [`EventService.java`](../src/main/java/com/bvicam/campusconnect/service/EventService.java) | `getAllEvents`, `getUpcomingEvents`, `createEvent`, `rsvpEvent`, `deleteEvent` | Service helper for event lifecycle and participant RSVP management. |
| [`EmailService.java`](../src/main/java/com/bvicam/campusconnect/service/EmailService.java) | `sendBroadcastEmail(emails, subject, content)` | `@Async` service that dispatches campus-wide broadcast emails on a background thread via `JavaMailSender`. |
| [`OllamaClientService.java`](../src/main/java/com/bvicam/campusconnect/service/OllamaClientService.java) | `generateChatResponse(prompt, query)`, `generateEmbedding(text)` | Local HTTP client connecting to Ollama daemon (`localhost:11434`) for chat (`gemma4:e4b`) and embeddings (`nomic-embed-text`) with timeout and offline fallback. |
| [`KnowledgeIngestionService.java`](../src/main/java/com/bvicam/campusconnect/service/KnowledgeIngestionService.java) | `ingestFile(...)`, `scrapeAndIndexUrl(...)`, `getAllDocuments`, `deleteDocument` | Extracts text from uploaded PDFs (Apache PDFBox) or web pages (Jsoup), partitions with `TextChunker`, generates vector embeddings, and persists document chunks. |
| [`CampusAiService.java`](../src/main/java/com/bvicam/campusconnect/service/CampusAiService.java) | `answerQuestion(question)` | Coordinates RAG retrieval over vector embeddings, injects real-time MySQL campus records (Events, Jobs, Batch), synthesizes prompts, and produces answers with citations. |

---

### 2.4 Repositories (`repository/`)

| Repository | Entity | Custom Queries |
| :--- | :--- | :--- |
| [`UserRepository.java`](../src/main/java/com/bvicam/campusconnect/repository/UserRepository.java) | `User` | `findByEmail`, `existsByEmail`, `findByBatchYear`, `searchUsers(role, batch, keyword)` (JPQL matching name, email, skills, company, headline, designation) |
| [`DepartmentRepository.java`](../src/main/java/com/bvicam/campusconnect/repository/DepartmentRepository.java) | `Department` | `findByCode(code)` |
| [`PostRepository.java`](../src/main/java/com/bvicam/campusconnect/repository/PostRepository.java) | `Post` | `findAllByOrderByIsPinnedDescCreatedAtDesc()` |
| [`CommentRepository.java`](../src/main/java/com/bvicam/campusconnect/repository/CommentRepository.java) | `Comment` | `findByPostIdOrderByCreatedAtAsc`, `findByPostIdAndParentCommentIsNullOrderByCreatedAtAsc`, `countByPostId` |
| [`EventRepository.java`](../src/main/java/com/bvicam/campusconnect/repository/EventRepository.java) | `Event` | `findByDateTimeAfterOrderByDateTimeAsc(now)` |
| [`JobRepository.java`](../src/main/java/com/bvicam/campusconnect/repository/JobRepository.java) | `Job` | `findAllByOrderByPostedAtDesc()` |
| [`PrivateMessageRepository.java`](../src/main/java/com/bvicam/campusconnect/repository/PrivateMessageRepository.java) | `PrivateMessage` | `findConversation(user1, user2)` (bidirectional JPQL ordered by `timestamp ASC`) |
| [`NotificationRepository.java`](../src/main/java/com/bvicam/campusconnect/repository/NotificationRepository.java) | `Notification` | `findTop10ByRecipientEmailOrderByCreatedAtDesc`, `findByRecipientEmailAndReadFalse`, `countByRecipientEmailAndReadFalse` |
| [`FeedbackRepository.java`](../src/main/java/com/bvicam/campusconnect/repository/FeedbackRepository.java) | `Feedback` | `findAllByOrderBySubmittedAtDesc()` |
| [`KnowledgeDocumentRepository.java`](../src/main/java/com/bvicam/campusconnect/repository/KnowledgeDocumentRepository.java) | `KnowledgeDocument` | `findAllByOrderByCreatedAtDesc()`, `findByCategoryOrderByCreatedAtDesc(category)` |
| [`DocumentChunkRepository.java`](../src/main/java/com/bvicam/campusconnect/repository/DocumentChunkRepository.java) | `DocumentChunk` | `findByDocumentIdOrderByChunkIndexAsc(docId)`, `findAllWithDocument()`, `findByDocumentCategory(category)` |

---

## 3. Frontend File Reference (`src/main/resources/static`)

### 3.1 HTML Entry Pages & Dynamic Component Views

| File | Loaded By | Purpose |
| :--- | :--- | :--- |
| [`index.html`](../src/main/resources/static/index.html) | Direct Browser Navigation | Public landing page showcasing BVICAM GradLink features, stats, alumni spotlights, and CTAs. |
| [`login.html`](../src/main/resources/static/login.html) | Direct Browser Navigation | Split-screen glassmorphic login page with password visibility toggle and error handling. |
| [`dashboard.html`](../src/main/resources/static/dashboard.html) | Post-Login Redirect | Main authenticated SPA shell containing the top navbar, notification bell, left sidebar, `#main-content` container, right-hand Global Chat, floating GradBot widget, and global modals (`#dmModal`, `#postJobModal`, `#editJobModal`, `#feedbackModal`). |
| [`components/wall.html`](../src/main/resources/static/components/wall.html) | `switchTab('wall')` | Campus Wall post composer (text + local photo upload + image URL) and feed container. |
| [`components/messages.html`](../src/main/resources/static/components/messages.html) | `switchTab('messages')` | Recent 1-on-1 direct message conversations list. |
| [`components/directory.html`](../src/main/resources/static/components/directory.html) | `switchTab('directory')` | Search bar, Role & Batch filter dropdowns, directory table, and `#userProfileModal`. |
| [`components/events.html`](../src/main/resources/static/components/events.html) | `switchTab('events')` | Events grid plus `#eventModal` (Create), `#editEventModal` (Edit), and `#participantsModal` (RSVPs). |
| [`components/jobs.html`](../src/main/resources/static/components/jobs.html) | `switchTab('jobs')` | Career Opportunities header, `+ Post Job / Referral` button (`#btnPostJob`), and `#jobList` container. |
| [`components/profile.html`](../src/main/resources/static/components/profile.html) | `switchTab('profile')` | Editable professional profile form (headline, company, designation, skills, bio, LinkedIn, GitHub) and notification toggles. |
| [`components/admin.html`](../src/main/resources/static/components/admin.html) | `switchTab('admin')` | 4-tab Admin Workspace (`Manage Users`, `Jobs & Referrals`, `Feedback & Ratings`, `Broadcast Email`) plus `#createUserModal` and `#editUserModal`. |

---

### 3.2 Frontend JavaScript Modules (`js/`)

| File | Key Exports / Global Functions | Responsibilities |
| :--- | :--- | :--- |
| [`js/utils/api.js`](../src/main/resources/static/js/utils/api.js) | `get()`, `send()`, `getCurrentUser()` | Centralized HTTP client that injects `Authorization: Bearer <jwt_token>`, handles `401` redirects, and reads current user identity from `localStorage`. |
| [`js/app.js`](../src/main/resources/static/js/app.js) | Login Form Submit Handler | Authenticates via `POST /api/auth/login`, saves `jwt_token` and user metadata in `localStorage`, and redirects to `dashboard.html`. |
| [`js/dashboard-main.js`](../src/main/resources/static/js/dashboard-main.js) | `window.switchTab()`, `window.logout()` | SPA router and lifecycle coordinator. Verifies auth, connects WebSockets, loads tab HTML fragments, hoists component `.modal` elements to `document.body`, and invokes module initializers. |
| [`js/modules/wall.js`](../src/main/resources/static/js/modules/wall.js) | `fetchPosts()`, `createPost()`, `likePost()`, `togglePin()`, `deletePost()`, `addComment()`, `addReply()`, `deleteComment()` | Manages the Campus Wall feed, multipart photo upload (`POST /api/posts/upload`), moderation error alerts, post pinning, likes, and nested comment threads. |
| [`js/modules/chat.js`](../src/main/resources/static/js/modules/chat.js) | `connectChat()`, `fetchRecentChats()`, `sendMessage()`, `startDirectChat()`, `sendDirectMessage()` | Manages the SockJS/STOMP connection (`/ws`), public chat room, 1-on-1 DM modal, live typing indicator (`#typingIndicator`), and unread message badges. |
| [`js/modules/directory.js`](../src/main/resources/static/js/modules/directory.js) | `runSearch()`, `debouncedSearch()`, `openUserProfile()`, `startDirectChatFromModal()` | Executes live debounced directory queries (`GET /api/users/search`), renders member rows, and populates `#userProfileModal`. |
| [`js/modules/events.js`](../src/main/resources/static/js/modules/events.js) | `fetchEvents()`, `publishNewEvent()`, `openEditEvent()`, `submitEditEvent()`, `toggleRSVP()`, `viewParticipants()`, `deleteEvent()` | Renders event cards, handles 1-click RSVPs, and provides Admin Create/Edit/Delete and RSVP participant list modals. |
| [`js/modules/jobs.js`](../src/main/resources/static/js/modules/jobs.js) | `fetchJobs()`, `postJob()`, `openEditJobModal()`, `submitEditJob()`, `deleteJob()` | Renders job/referral cards, reveals `+ Post Job / Referral` and `Edit`/`Delete` controls for `ADMIN` and `ALUMNI`, and normalizes `mailto:` / `https://` application links. |
| [`js/modules/admin.js`](../src/main/resources/static/js/modules/admin.js) | `loadAdminModule()`, `openCreateUserModal()`, `adminCreateUser()`, `openEditUserModal()`, `adminSaveUser()`, `deleteUser()`, `unflagUser()`, `adminResetPassword()`, `uploadBulkDataExcel()`, `sendBroadcastEmail()`, `loadAdminFeedback()`, `loadAdminJobs()` | Powers the entire Admin Operations Workspace across Users, Bulk Excel/CSV Upload, Jobs & Referrals, Feedback, and Email Broadcasts. |
| [`js/modules/notifications.js`](../src/main/resources/static/js/modules/notifications.js) | `initNotifications()`, `handleIncomingNotification()` | Loads unread counts and recent alerts from `/api/notifications`, renders the bell dropdown, shows live Bootstrap toasts, and navigates to the target tab on click. |
| [`js/modules/profile.js`](../src/main/resources/static/js/modules/profile.js) | `loadProfile()`, `saveProfile()` | Loads `/api/users/me` into the Profile Settings form and saves updates via `PUT /api/users/profile`. |
| [`js/modules/feedback.js`](../src/main/resources/static/js/modules/feedback.js) | `openFeedbackModal()`, `setRating()`, `submitFeedback()` | Powers the interactive 1–5 star rating modal (`POST /api/feedback`). |
| [`js/modules/bot.js`](../src/main/resources/static/js/modules/bot.js) | `initBot()`, `toggleBot()`, `askBot()` | Controls the floating GradBot assistant window (`POST /api/bot/ask`). |
