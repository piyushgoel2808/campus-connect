Campus Connect — Project Report

Abstract
--------
Campus Connect is a unified, real-time campus networking platform built with Spring Boot and WebSockets. It provides secure role-based access for students, alumni, and administrators and includes features such as real-time public and private chat, event management with RSVP, a searchable user directory, a jobs board, a memory wall, and an admin bulk-onboarding workflow.

Project Summary
---------------
- Aim: Provide a private, secure campus community that supports communication, events, and career opportunities.
- Tech stack: Java 17, Spring Boot 3.x, Spring Security (JWT), Spring WebSockets (STOMP + SockJS), Spring Data JPA/Hibernate, Maven; frontend uses HTML/CSS/Vanilla JS and Bootstrap 5.
- Database: MySQL-compatible (TiDB used in deployment notes).
- Deployment: Dockerfile included; intended hosting on Render or similar.

Key Features
------------
- Authentication & Role Management: JWT-based authentication with roles `ADMIN`, `STUDENT`, and `ALUMNI`. Admins can bulk onboard users via CSV/XLSX.
- Real-Time Communication: Global chat and private messaging powered by Spring STOMP WebSockets and SockJS; persistent private message history.
- Event Management: Create, edit, delete events; RSVP toggle; participant listing.
- Community & Careers: Searchable user directory, job board for postings, and a memory wall for posts and likes.
- Feedback & Bot: Feedback submission for admins and a simple rule-based bot endpoint for FAQs.

Architecture & Structure
------------------------
- Layered Spring architecture: Controllers → Services → Repositories → Entities. DTOs are used to shape API responses where needed.
- Web: Static resources under `src/main/resources/static/` provide the landing, login, and dashboard shell. The dashboard loads HTML fragments as components and initializes modules on demand.
- Persistence: Spring Data JPA repositories with several custom queries to support searching, ordered feeds, and conversation history.

Important Components
--------------------
- `CampusConnectApplication` — Spring Boot entry point; includes `DataSeeder` for initial data.
- Security: `SecurityConfig`, `JwtAuthenticationFilter`, `JwtUtil`, and `CustomUserDetailsService` implement stateless JWT authentication and password hashing (BCrypt).
- Controllers: REST controllers for auth, users, admin, events, jobs, posts, feedback, and chat (STOMP endpoints). `AdminController` handles bulk uploads and email broadcasts.
- Frontend modules: `js/dashboard-main.js`, `js/utils/api.js`, and feature modules like `directory.js`, `chat.js`, `events.js`, `jobs.js`, `wall.js`, and `profile.js`.

Data Model Overview
-------------------
- `User`: core entity (email, password hash, name, role, academic and professional fields).
- `Event`: title, description, date/time, location, organizer, target department, many-to-many participants.
- `Job`, `Post`, `Feedback`, `PrivateMessage` and `Department` — domain entities supporting the app features.

Security & Production Considerations
-----------------------------------
- Current status: JWT secret and some credentials are present in source `application.properties` — move these to environment variables for production.
- Authorization: many role checks are implemented inside controllers/services; consider centralizing route-level role policies in `SecurityConfig` for improved maintainability.
- Secrets: Remove hard-coded secrets from source and use environment-based configuration or a secrets manager.

Known Risks & Caveats
---------------------
- Mixed use of DTOs and entity-backed JSON responses can create inconsistent API contracts.
- Chat persistence uses multiple models (`ChatMessage` DTO vs `PrivateMessage` entity) — consolidate into a single canonical history model.
- Frontend relies on `localStorage` for session state and uses global `window` functions; this can lead to stale-session issues and is harder to scale.
- Bulk upload defaults to a predictable password (`Bvicam@2025`) — ensure admins rotate defaults and require password changes on first login.

Setup & Run (Local Development)
-------------------------------
Prerequisites:
- JDK 17
- Maven 3.8+
- MySQL (or compatible) or provide TiDB connection details

Basic steps:
1. Copy and edit `src/main/resources/application.properties` or provide environment variables for DB, mail, and JWT secret.
2. Build: `mvn clean package` (or use `./mvnw` on *nix, `mvnw.cmd` on Windows).
3. Run: `java -jar target/*.jar` (or `mvn spring-boot:run`).
4. Open the landing page at `http://localhost:8080` and log in via `login.html`.

Notes on WebSocket:
- Endpoint: `/ws` (SockJS + STOMP). The dashboard connects automatically when a valid JWT is present in localStorage.

Recommended Improvements
------------------------
- Move secrets to environment variables and use Spring's `@Value` with fallbacks.
- Add central authorization rules in `SecurityConfig` and rely less on inline role checks.
- Consolidate chat persistence and add integration tests for messaging flows.
- Refactor frontend to reduce global `window` mutations and encapsulate module state.
- Add CI build verification and basic integration tests for auth, events, and messaging.

Appendix — References
---------------------
- See `README.md` for the high-level project description.
- See `PROJECT_ANALYSIS.md` for a deeper codebase walkthrough and suggested next steps.
- Helpful docs: `HELP.md` in the repository.


---

If you'd like, I can:
- Convert this draft into a Word document (`.docx`) and replace `Campus Connect.doc` (requires confirmation).
- Incorporate more specific text from other files (tests, example seed files, or screenshots).
- Generate a one-page summary slide or PDF for presentations.
