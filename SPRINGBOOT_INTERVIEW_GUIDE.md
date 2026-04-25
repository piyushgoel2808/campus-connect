# Spring Boot Interview Guide for Campus Connect

## What This Project Is
Campus Connect is a full-stack Spring Boot application for a college community. It combines:

- Spring Boot for backend application setup.
- Spring Security and JWT for authentication.
- Spring Data JPA for database access.
- Spring WebSocket/STOMP for real-time chat.
- Vanilla JavaScript and HTML fragments for the frontend.
- MySQL-compatible persistence for users, events, posts, jobs, feedback, and messages.

If you are asked about this project in an interview, the strongest answer is that it is a layered campus portal with secure login, role-based access, real-time communication, and relational data management.

## High-Level Architecture
The app follows a standard layered Spring Boot structure:

1. Frontend pages and component fragments live under `src/main/resources/static`.
2. The browser calls REST APIs under `/api/...`.
3. Controllers receive requests and delegate to services or repositories.
4. Services contain business logic and transactional behavior.
5. Repositories talk to the database through Spring Data JPA.
6. Entities define the database tables and relationships.
7. Security filters validate JWT tokens before requests reach protected endpoints.

In interview terms, this is a clean example of separation of concerns.

## Folder Map And What Each Folder Does

### `src/main/java/com/bvicam/campusconnect`
This is the main backend code.

- `CampusConnectApplication.java` starts the Spring Boot application.
- `config/` contains security, WebSocket, JWT filtering, and data seeding.
- `controller/` contains API endpoints.
- `service/` contains reusable business logic.
- `repository/` contains JPA repository interfaces.
- `entity/` contains persistent database models.
- `dto/` contains request and response models used by the frontend.
- `util/` contains JWT helper logic.

### `src/main/resources`
This is where static frontend files and application configuration live.

- `application.properties` contains datasource, mail, and JPA configuration.
- `static/index.html` is the landing page.
- `static/login.html` is the login screen.
- `static/dashboard.html` is the main authenticated shell.
- `static/components/` contains reusable page fragments loaded into the dashboard.
- `static/js/` contains all frontend logic.

### `src/test/java`
Contains the Spring Boot application context test.

### Root files
- `pom.xml` defines dependencies and build plugins.
- `README.md` describes the project at a high level.
- `Dockerfile` supports container deployment.

## Spring Boot Concepts You Should Know From This Project

### 1. `@SpringBootApplication`
This annotation is the main Spring Boot startup annotation.

It combines:
- `@Configuration`
- `@EnableAutoConfiguration`
- `@ComponentScan`

In this project, `CampusConnectApplication` uses it to bootstrap the entire app.

### 2. Auto Configuration
Spring Boot automatically configures many things based on dependencies in `pom.xml`.

Here, Spring Boot auto-configures:
- the embedded web server,
- Jackson JSON support,
- Spring MVC request handling,
- JPA and Hibernate integration,
- Spring Security infrastructure,
- WebSocket support,
- mail support.

This is one of the biggest interview concepts: Spring Boot reduces boilerplate by auto-configuring common application pieces.

### 3. Dependency Injection
Spring creates and injects beans into classes using `@Autowired`, constructors, or both.

In this project, dependency injection is used for:
- repositories inside controllers and services,
- JWT utilities inside security filters,
- password encoders inside auth and admin flows,
- message templates inside chat controllers.

Interview point: dependency injection makes the code loosely coupled and testable.

### 4. REST Controllers
Classes annotated with `@RestController` return data directly as JSON.

This project uses REST controllers for:
- authentication,
- users,
- admin functions,
- events,
- jobs,
- posts,
- feedback,
- bot answers.

### 5. `@Controller`
`ChatController` is a hybrid controller because it handles WebSocket message mappings and REST endpoints.

### 6. Service Layer
Services contain business logic that should not live inside controllers.

In this project:
- `PostService` handles feed ordering, likes, pinning, and permissions.
- `EventService` handles event persistence and RSVP logic.
- `EmailService` sends broadcast emails asynchronously.
- `CustomUserDetailsService` loads users for Spring Security.

### 7. Repository Layer
Repositories extend `JpaRepository`, which gives CRUD functionality without writing SQL.

This project also uses custom query methods like:
- search users by role, batch, and keyword,
- fetch upcoming events,
- sort posts with pinned items first,
- fetch conversation history between two users.

### 8. Entity Layer
Entities map Java objects to database tables.

Examples:
- `User`
- `Event`
- `Job`
- `Post`
- `Feedback`
- `PrivateMessage`
- `Department`

### 9. DTOs
DTOs are used to shape data for requests and responses.

Why use them:
- reduce exposure of internal entity fields,
- avoid recursion issues in JSON,
- keep API responses cleaner,
- send calculated fields like participant count and attendance.

### 10. Security
Security is built using:
- JWT tokens,
- Spring Security filter chain,
- BCrypt password hashing,
- stateless sessions,
- authorization checks.

### 11. Transactions
Methods marked with `@Transactional` ensure database changes commit correctly and stay consistent.

### 12. Async Processing
`@Async` is used in the email service so broadcast emails run in the background.

### 13. WebSockets
WebSockets are used for live chat and typing indicators.

### 14. JPA/Hibernate Relationship Mapping
This project uses one-to-many, many-to-one, and many-to-many mappings to represent real-world relationships.

## Startup Flow
When the app starts:

1. Spring Boot launches the application.
2. Beans are created from configuration and component classes.
3. Security filters are registered.
4. WebSocket endpoints are exposed.
5. JPA repositories become available.
6. `DataSeeder` runs and creates sample users, departments, posts, and jobs if missing.

This is important in an interview because it shows you understand what happens before the user even opens the app.

## Authentication Flow

### Login
1. The user enters email and password on `login.html`.
2. `app.js` sends the credentials to `POST /api/auth/login`.
3. `AuthController` uses `AuthenticationManager` to validate the password.
4. `CustomUserDetailsService` loads the stored user by email.
5. `JwtUtil` generates a signed JWT token.
6. The frontend stores the token and user metadata in `localStorage`.

### Why JWT Is Used
JWT makes the app stateless.

Instead of storing server sessions for every login, the backend trusts a signed token sent by the client on each request.

Interview answer: JWT is useful for stateless APIs and works well with frontend apps that make many independent requests.

### Request Authentication
1. The browser sends the JWT in the `Authorization: Bearer ...` header.
2. `JwtAuthenticationFilter` intercepts the request.
3. The token is parsed and validated.
4. The user is loaded from the database.
5. The authenticated user is placed into the Spring Security context.
6. Protected endpoints can now identify the caller.

## Security Flow
`SecurityConfig` defines how requests are protected.

What it does:
- disables CSRF for this API-driven app,
- configures CORS,
- sets the session policy to stateless,
- allows public access to login pages, static assets, and auth endpoints,
- protects the rest of the APIs,
- inserts the JWT filter before username/password authentication.

### Interview Notes On Security
You should be able to explain these points:

- Why stateless security is appropriate here.
- Why CSRF is often disabled for token-based APIs.
- Why the JWT filter must run before protected requests are evaluated.
- Why passwords are stored as hashes instead of plain text.
- Why roles matter for admin and alumni features.

## Database Flow
The project uses Spring Data JPA with a MySQL-compatible datasource.

### How Database Work Happens
1. A controller calls a service or repository.
2. The repository issues JPA operations.
3. Hibernate translates Java entities into SQL.
4. The database stores the resulting rows and relationships.

### Important Configuration
`application.properties` sets:
- database URL,
- username and password,
- Hibernate `ddl-auto=update`,
- SQL logging,
- MySQL dialect,
- mail server configuration.

### What `ddl-auto=update` Means
Hibernate will update the schema automatically based on entity changes.

Good for development, not ideal for production.

### Why Entities Matter
Entities define:
- table names,
- primary keys,
- column constraints,
- relationships,
- serialization behavior.

## Entity Relationships

### `User`
The central entity. It stores identity, role, profile information, and relationships.

Important fields:
- `email`
- `passwordHash`
- `name`
- `role`
- `batchYear`
- `enrollmentNumber`
- profile links and work history

Relations:
- one user can author many posts,
- one user can post many jobs,
- one user can submit many feedback entries,
- users can attend many events,
- users belong to a department.

### `Event`
Represents a campus event.

Fields include:
- title,
- description,
- date and time,
- location,
- target department,
- participants.

The many-to-many relationship with users is what powers RSVP.

### `Post`
Represents wall posts.

Fields include:
- content,
- image URL,
- author,
- likes,
- pinned flag,
- created timestamp.

### `Job`
Represents job or internship listings.

Fields include:
- title,
- company,
- location,
- description,
- application link,
- postedBy user,
- postedAt timestamp.

### `Feedback`
Stores star rating, comments, and the submitting user.

### `PrivateMessage`
Stores persistent private chat messages using sender email, receiver email, content, and timestamp.

### `Department`
Stores department name and code.

### `Role`
An enum with values:
- `STUDENT`
- `ALUMNI`
- `ADMIN`

## Controllers And What They Do

### `AuthController`
Handles register, login, and password change.

### `AdminController`
Handles:
- managing users,
- resetting passwords,
- deleting posts,
- sending broadcasts,
- uploading users from Excel or CSV.

### `UserController`
Handles current profile, directory listing, search, and profile updates.

### `EventController`
Handles event CRUD, RSVP toggling, and participant lists.

### `JobController`
Handles job listing, posting, and deletion.

### `PostController`
Handles wall posts.

### `FeedbackController`
Handles feedback.

### `ChatController`
Handles chat.

### `BotController`
Returns simple rule-based answers for common questions.

## Services And What They Do

### `PostService`
Controls feed ordering, post creation, deletion, likes, and pinning.

### `EventService`
Wraps event persistence and RSVP logic.

### `EmailService`
Sends broadcast emails to all users in the background.

### `CustomUserDetailsService`
Loads a user by email so Spring Security can authenticate the request.

## Repository Responsibilities
Repositories are thin interfaces, but they are very important in Spring Boot.

### `UserRepository`
Finds users by email and supports searchable filtering.

### `EventRepository`
Returns events and upcoming event lists.

### `PostRepository`
Returns feed items in pinned/newest order.

### `JobRepository`
Returns jobs newest first.

### `FeedbackRepository`
Returns feedback newest first.

### `PrivateMessageRepository`
Returns all messages in a conversation between two users.

### Interview Point
In a Spring Boot interview, be ready to say that repositories abstract database access and remove the need to write most SQL manually.

## Frontend Connection To Backend
This is one of the most important topics for your interview.

### How The Frontend Talks To Spring Boot
1. The user opens the dashboard in the browser.
2. JavaScript reads the saved JWT from `localStorage`.
3. `js/utils/api.js` adds that token to request headers.
4. Fetch calls go to `/api/...` endpoints.
5. The backend returns JSON.
6. The frontend renders the response into HTML.

### Dashboard Loading Model
`dashboard.html` is not a full React or Angular app. It is a shell page that loads feature fragments.

`dashboard-main.js`:
- loads the correct component HTML,
- imports the correct module script,
- initializes the feature logic,
- keeps the page feeling like a single application.

### Component Examples
- `components/wall.html` pairs with `modules/wall.js`.
- `components/events.html` pairs with `modules/events.js`.
- `components/jobs.html` pairs with `modules/jobs.js`.
- `components/directory.html` pairs with `modules/directory.js`.
- `components/profile.html` pairs with `modules/profile.js`.
- `components/admin.html` pairs with `modules/admin.js`.
- `components/messages.html` pairs with `modules/chat.js`.

### Local Storage Usage
The frontend stores:
- JWT token,
- user name,
- user role,
- user email,
- optional user ID.

This is how the browser remembers the session across page reloads.

## WebSocket Flow
The real-time chat feature is one of the strongest interview topics in this project.

### WebSocket Setup
`WebSocketConfig`:
- registers the STOMP endpoint `/ws`,
- enables a simple broker,
- sets `/app` as the sending prefix,
- uses `/user` for user-specific destinations.

### Public Chat
1. The browser connects to `/ws` using SockJS and STOMP.
2. It subscribes to `/topic/public`.
3. A message sent to `/app/chat.sendMessage` is broadcast to everyone subscribed.

### Private Chat
1. The browser sends a message to `/app/chat.private`.
2. The backend stores the message in the database.
3. The backend pushes the message to the receiver queue.
4. The backend also echoes it back to the sender queue.
5. The frontend displays the live update.

### Typing Indicator
The frontend sends typing state to `/app/chat.typing` and the server routes that to the target user.

### Interview Explanation
WebSockets are used here because chat needs low-latency, bidirectional communication, unlike plain REST polling.

## File To Function Mapping

### `CampusConnectApplication.java`
Starts the app.

### `SecurityConfig.java`
Defines access rules, CORS, password encoding, and filter chain behavior.

### `JwtAuthenticationFilter.java`
Reads the token from requests and authenticates the user.

### `JwtUtil.java`
Creates and validates signed JWTs.

### `DataSeeder.java`
Adds initial demo data on startup.

### `AuthController.java`
Handles login and registration.

### `UserController.java`
Handles directory and profile actions.

### `EventController.java`
Handles events and RSVP.

### `JobController.java`
Handles jobs.

### `PostController.java`
Handles wall posts.

### `FeedbackController.java`
Handles feedback.

### `ChatController.java`
Handles chat.

### `BotController.java`
Handles bot replies.

### `PostService.java`
Implements feed business rules.

### `EventService.java`
Implements event business rules.

### `EmailService.java`
Implements broadcast mail.

### `CustomUserDetailsService.java`
Integrates users with Spring Security.

## Major Spring Boot Features To Mention In An Interview

### Auto Configuration
Spring Boot wires the app automatically based on dependencies.

### Starter Dependencies
`pom.xml` uses starter packages so you do not manually manage every Spring library.

### Embedded Server
The app runs like a self-contained Java application, not a separately deployed WAR.

### REST APIs
Spring MVC controllers return JSON to the frontend.

### Dependency Injection
Beans are injected instead of manually constructed.

### JPA/Hibernate
Database access is handled using entity classes and repository interfaces.

### Security
Spring Security protects endpoints and validates tokens.

### WebSockets
Used for real-time communication.

### Transaction Management
Ensures data consistency for create/update/delete operations.

### Async Work
Broadcast email runs outside the main request thread.

## Good Interview Answers You Can Give

### What is Spring Boot?
Spring Boot is a framework that simplifies Spring application development by providing auto-configuration, embedded servers, and production-friendly defaults.

### What is the role of a controller?
It accepts HTTP requests, validates or extracts input, and delegates work to the service layer.

### Why use DTOs?
To avoid exposing full entities, reduce coupling, and shape API responses for the frontend.

### Why use JPA repositories?
They remove most boilerplate database code and let you focus on business logic.

### Why use JWT?
JWT supports stateless authentication and is well suited for frontend-backend applications.

### Why use services?
Services hold reusable business logic and keep controllers thin.

### Why use WebSockets for chat?
Chat requires live two-way communication, which is more efficient than constant polling.

### Why use `@Transactional`?
It ensures database changes are committed atomically and prevents partial updates.

## Important Caveats In This Codebase

- Some permission checks are done manually inside methods rather than through centralized security rules.
- The project uses both entity and DTO shapes in API responses, so frontend contracts are not uniform everywhere.
- JWT secret handling should be moved out of source code for production use.
- Default credentials and mail settings in `application.properties` are for local development only.
- The frontend uses global window functions heavily, which is acceptable for a small project but less ideal for scaling.

## How To Explain The Whole App In One Interview Minute
Campus Connect is a Spring Boot campus community platform with JWT authentication, role-based access, MySQL-backed persistence, event RSVP, job posting, wall posts, feedback, and real-time public/private chat using WebSockets. The backend is split into controllers, services, repositories, entities, DTOs, and security filters, while the frontend is a modular HTML and JavaScript dashboard that loads feature components dynamically and communicates with the backend through REST APIs and WebSocket subscriptions.

## Final Summary
If you remember only one thing for the interview, remember this:

Spring Boot gives structure, security, database access, and deployment simplicity. In this project, those features are used to build a complete campus network where the frontend sends requests, the backend authenticates and processes them, the database stores state, and WebSockets keep chat live.