# Campus Connect Project Analysis

## Overview
Campus Connect is a Spring Boot-based campus networking platform for BVICAM students, alumni, and administrators. The application combines JWT-secured REST APIs, Spring Security, JPA/Hibernate persistence, and Spring WebSockets/STOMP for real-time chat. The browser side is a lightweight HTML/CSS/Vanilla JavaScript frontend that loads tab-based components dynamically inside a single dashboard.

The codebase is organized around a clear product idea: one private campus community that supports authentication, profile management, user search, events, jobs, posts, feedback, admin operations, and both public and private messaging.

## Repository Structure

### Backend
- `src/main/java/com/bvicam/campusconnect/CampusConnectApplication.java` - Spring Boot entry point.
- `config/` - Security, WebSocket, JWT filter, and data seeding configuration.
- `controller/` - REST and STOMP endpoints for authentication, admin, users, events, jobs, posts, feedback, chat, and a small bot endpoint.
- `service/` - Business logic for posts, events, email broadcasts, and user lookup.
- `repository/` - Spring Data JPA repositories.
- `entity/` - Database entities and enums.
- `dto/` - Request/response objects used by controllers and the UI.
- `util/` - JWT helper.

### Frontend
- `src/main/resources/static/index.html` - Landing page.
- `src/main/resources/static/login.html` - Login page.
- `src/main/resources/static/dashboard.html` - Single-page dashboard shell.
- `src/main/resources/static/components/` - HTML fragments loaded into the dashboard.
- `src/main/resources/static/js/` - Core app bootstrap and feature modules.

### Supporting Files
- `pom.xml` - Maven build and dependency definition.
- `application.properties` - Local database, mail, and JPA configuration.
- `README.md` - High-level project description.
- `src/test/java/.../CampusConnectApplicationTests.java` - Basic Spring context test.
- `bulk1.xlsx`, `bulk1 - Copy.xlsx` - Sample bulk-upload data files.

## Architecture Summary
The project follows a conventional layered Spring architecture:

- Controllers expose HTTP and WebSocket endpoints.
- Services contain business logic and transactional behavior.
- Repositories encapsulate persistence operations.
- Entities model the relational schema.
- DTOs keep request and response payloads separated from entities where needed.
- The frontend uses `fetch`, JWT stored in `localStorage`, and dynamic component loading inside `dashboard.html`.

This is not a full SPA framework application. Instead, the dashboard acts like a shell that swaps HTML fragments and initializes module scripts on demand.

## Backend Deep Dive

### Application Startup
`CampusConnectApplication` enables Spring Boot auto-configuration and async execution. Startup is supplemented by `DataSeeder`, which creates initial users and departments if they do not exist.

### Security
`SecurityConfig` sets up:
- Stateless session policy.
- JWT filter insertion before username/password authentication.
- Permitted static resources and authentication endpoints.
- CORS with permissive origin patterns.
- BCrypt password encoding.

`JwtAuthenticationFilter` reads the `Authorization: Bearer <token>` header, parses the token with `JwtUtil`, loads the user through `CustomUserDetailsService`, and places the authenticated user into the security context.

Important detail: role-based authorization is only partially enforced at the security layer. Most role checks happen inside controller or service methods rather than through explicit route-level security rules.

### Authentication Flow
`AuthController` supports:
- `POST /api/auth/register` - Creates a user, sets the role, optionally attaches a department, and hashes the password.
- `POST /api/auth/login` - Authenticates credentials and returns a JWT plus user name and role.
- `POST /api/auth/change-password` - Updates the stored password and marks the password as changed.

The login response currently returns token, name, and role. The frontend stores these values in `localStorage` and uses the token for subsequent requests.

### Admin Operations
`AdminController` is the most feature-dense backend controller. It supports:
- Listing all users.
- Creating users with a default password.
- Updating user profiles.
- Resetting passwords.
- Deleting users.
- Deleting posts.
- Broadcasting emails to all users.
- Bulk user upload from CSV or Excel.

The bulk upload logic uses Apache POI for `.xlsx` files and a simple CSV parser for `.csv` files. Imported records default to the password `Bvicam@2025` and `passwordChanged=false`.

### User Directory and Profile
`UserController` provides:
- `GET /api/users/me` - Returns the current authenticated user.
- `GET /api/users` - Returns the searchable directory view.
- `GET /api/users/search` - Searches by role, batch, and keyword.
- `PUT /api/users/profile` - Updates the current user’s profile fields.

The directory response is mapped into `UserProfileDto` to avoid exposing the full entity shape to the browser.

### Events
`EventController` supports event lifecycle and RSVP logic:
- `GET /api/events` - Returns all events with calculated attendance state and participant count.
- `POST /api/events` - Creates an event.
- `PUT /api/events/{id}` - Updates an event.
- `POST /api/events/{id}/rsvp` - Toggles attendance for the authenticated user.
- `GET /api/events/{id}/participants` - Returns participant details.
- `DELETE /api/events/{id}` - Deletes an event.

`EventService` duplicates some of the same operations, but the controller currently owns the main request flow.

### Jobs
`JobController` exposes:
- `GET /api/jobs` - Lists jobs newest-first.
- `POST /api/jobs` - Allows alumni and admins to post jobs.
- `DELETE /api/jobs/{id}` - Allows the poster or admin to delete.

The controller checks role names directly, which is simple but less robust than a centralized authorization policy.

### Posts / Memory Wall
`PostController` and `PostService` implement the wall feed:
- Posts are returned pinned-first, then newest-first.
- Users can create posts and attach an optional image URL.
- Likes are incremented with a dedicated endpoint.
- Admins can pin or unpin posts.
- Posts can be deleted by the author or an admin.

The feed is ordered by `isPinned` and `createdAt`, so the pinning feature directly affects the presentation layer.

### Feedback
`FeedbackController` lets authenticated users submit feedback and lets admins read all feedback newest-first. The frontend exposes this through a modal rating widget.

### Chat and Messaging
`ChatController` handles both public and private messaging.

Public chat:
- `@MessageMapping("/chat.sendMessage")` broadcasts to `/topic/public`.
- `@MessageMapping("/chat.addUser")` also broadcasts join notifications.

Private chat:
- `@MessageMapping("/chat.private")` stores a `PrivateMessage` and sends it to the sender and receiver queues.
- `@MessageMapping("/chat.typing")` relays typing state to the target user.
- `GET /api/messages/history` returns the conversation history between two users.
- `GET /api/messages/partners` returns recent conversation partners.

The implementation uses email addresses as the chat identity rather than a separate messaging handle.

### Bot Endpoint
`BotController` is a rule-based helper endpoint. It inspects the question text and returns canned answers for:
- placement stats,
- batch lookups,
- contact info,
- syllabus references,
- upcoming events.

This is a lightweight FAQ bot, not an ML-powered assistant.

## Domain Model

### `User`
The central entity. It stores:
- identity and login fields: email, password hash, name, role,
- academic data: enrollment number, batch year, department,
- professional data: skills, headline, company, designation, experience, links,
- chat relationships and events.

It is carefully protected with `@JsonIgnore` on password and recursive associations.

### `Department`
Simple lookup entity with unique `name` and `code`. Used to classify users and target content.

### `Event`
Represents campus events, with:
- title, description, date/time, location, organizer,
- target department,
- many-to-many participants.

Equality is based on `id` only, which avoids recursion and collection issues.

### `Job`
Represents an internship or job post with title, company, location, description, apply link, poster, timestamp, and optional target department.

### `Post`
Represents wall updates. Fields include content, optional image URL, author, creation time, like count, and pinned state.

### `Feedback`
Captures rating, comments, submitter, and timestamp.

### `PrivateMessage`
Stores persistent private message history using sender and receiver email addresses plus timestamp.

### `ChatMessage`
Looks like a richer chat entity with sender and recipient relationships. In practice, the active private chat persistence path uses `PrivateMessage`, while public/private websocket payloads use the DTO `ChatMessage`.

### `Role`
Enum with `STUDENT`, `ALUMNI`, and `ADMIN`.

## DTO Layer
The DTOs are used to keep browser-facing payloads simple and to reduce entity leakage.

- `RegisterRequest` - Registration input.
- `LoginRequest` - Login input.
- `AuthResponse` - Login success response.
- `UserProfileDto` - Editable profile data shown in the UI.
- `EventDTO` - Event list response with computed `attending` and `participantCount`.
- `com.bvicam.campusconnect.dto.ChatMessage` - WebSocket message payload for chat.
- `TypingMessage` - Typing-indicator payload.

## Persistence Layer
Repositories are mostly standard Spring Data interfaces with a few custom queries:
- `UserRepository.searchUsers(...)` supports role, batch, and keyword search.
- `EventRepository.findByDateTimeAfterOrderByDateTimeAsc(...)` is used for upcoming events.
- `PostRepository.findAllByOrderByIsPinnedDescCreatedAtDesc()` supports feed ordering.
- `JobRepository.findAllByOrderByPostedAtDesc()` supports reverse chronological job listing.
- `FeedbackRepository.findAllByOrderBySubmittedAtDesc()` supports admin review.
- `PrivateMessageRepository.findConversation(...)` returns one-on-one chat history.

## Frontend Deep Dive

### Landing Page
`index.html` is a marketing-style public homepage. It presents:
- the platform mission,
- feature cards,
- call-to-action buttons,
- college branding,
- contact and footer information.

### Login Page
`login.html` is a simple centered form. The JavaScript in `js/app.js` posts credentials to `/api/auth/login`, stores token and user info in `localStorage`, then redirects to the dashboard.

### Dashboard Shell
`dashboard.html` is the main authenticated experience. It contains:
- a left navigation rail,
- a central content panel that swaps components,
- a global chat column on desktop,
- modals for direct chat, job creation, profile viewing, admin editing, event editing, feedback, participants, and bot interaction.

This layout is intentionally modular. Most feature UI is loaded as fragments into the `main-content` region.

### Dashboard Bootstrap
`js/dashboard-main.js` is the entry coordinator:
- checks local storage for the current user,
- redirects to login if missing,
- reveals admin-only navigation,
- connects WebSocket chat,
- loads the default tab,
- handles tab switching and component loading,
- exposes logout behavior.

### API Helper
`js/utils/api.js` centralizes authenticated requests. It attaches the JWT to outgoing requests and redirects to login if a request returns `401`.

### Feature Modules
- `directory.js` - Searchable directory and profile popups.
- `chat.js` - Global chat, private chat, typing indicators, recent chats.
- `events.js` - Event listing, RSVP, admin create/edit/delete, participants modal.
- `jobs.js` - Job listing, posting, deletion.
- `wall.js` - Feed rendering, create/delete/like/pin post logic.
- `profile.js` - Load and save the current user profile.
- `feedback.js` - Star-based feedback submission.
- `admin.js` - Admin user management, bulk upload, password reset, feedback view.

## Main User Journeys

### Authentication and Session
1. User logs in through `login.html`.
2. JWT, name, role, and email are stored in `localStorage`.
3. Dashboard bootstrap checks for a saved session.
4. All API calls include the token in `Authorization`.

### Directory Search
1. User opens the directory tab.
2. JavaScript calls `/api/users/search` with role, batch, and keyword filters.
3. Results are rendered into a table.
4. A user can open a profile card or start a direct chat.

### Private Messaging
1. Dashboard connects to `/ws` using SockJS and STOMP.
2. Messages are sent to `/app/chat.private`.
3. Backend stores the message and forwards it to sender and receiver queues.
4. Frontend loads conversation history from REST endpoints and live updates via WebSocket subscriptions.

### Event RSVP
1. Events are fetched from `/api/events`.
2. The backend calculates attendance and participant count.
3. RSVP toggles call `POST /api/events/{id}/rsvp`.
4. Admins can view participants and edit or delete events.

### Feed Posting
1. User writes a wall post.
2. Frontend posts to `/api/posts`.
3. Feed renders pinned posts first.
4. Admins can pin/unpin or delete posts.

### Admin Bulk Onboarding
1. Admin opens the user management tab.
2. Uploads a CSV or Excel file.
3. Backend parses the file and creates users with defaults.
4. The UI refreshes the user table after upload.

## Strengths
- Clear separation of controllers, services, repositories, entities, and frontend modules.
- Real-time chat support with both public and private channels.
- A practical admin workflow for onboarding users and managing content.
- DTO usage where the UI needs computed fields or a safer payload shape.
- Seed data that helps new installs show meaningful content immediately.
- The frontend is organized into feature modules instead of a single large script.

## Risks and Caveats
- Some authorization checks are implemented manually inside controllers and services rather than centrally in security rules.
- The project mixes entity-backed JSON responses with DTO-backed responses, which can make frontend contracts less uniform.
- There are overlapping concepts in chat persistence: `ChatMessage` entity, `PrivateMessage` entity, and `ChatMessage` DTO.
- `JwtUtil` stores the secret in source code, which is not suitable for production.
- `application.properties` contains default local credentials and placeholder mail values.
- The frontend depends heavily on `localStorage`, so session corruption or stale data can create confusing states.
- The dashboard uses global `window` functions for many actions, which works but is hard to scale.
- I could not complete a local Maven verification from this environment because `mvn` was not installed, and the wrapper command did not provide a usable build transcript here.

## Build and Runtime Notes
- Target runtime is Java 17 with Spring Boot and MySQL-compatible storage.
- WebSocket endpoint is `/ws`.
- REST APIs are under `/api/...`.
- Static assets are served from `src/main/resources/static`.
- The sample data and dashboard assume seeded users and departments exist.

## Suggested Next Steps
- Move secrets such as JWT signing keys and mail credentials out of source and into environment variables.
- Consolidate chat persistence so there is one clear model for private message history.
- Add centralized role-based authorization for sensitive endpoints.
- Replace manual `window`-based client wiring with a more maintainable module pattern if the frontend grows further.
- Add integration tests for authentication, events, posts, and WebSocket chat.

## Conclusion
Campus Connect is a feature-rich, monolithic campus community app with a strong demo-friendly scope. The backend covers authentication, directory search, events, jobs, posts, feedback, and real-time messaging, while the frontend presents them through a modular dashboard shell. The code is already fairly complete for a student/alumni portal, but production hardening would require better secret handling, tighter authorization, and more test coverage.
