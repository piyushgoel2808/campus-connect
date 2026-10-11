# 🏗️ System Architecture — Campus Connect (GradLink | BVICAM)

> **Live Deployment:** [https://campus-connect-live.onrender.com](https://campus-connect-live.onrender.com)

This document provides a comprehensive technical overview of the **Campus Connect (GradLink)** architecture, covering its layered Spring Boot backend, lightweight Single-Page Application (SPA) frontend shell, real-time WebSocket/STOMP messaging backbone, security filter chain, and cloud/container deployment topology.

---

## 1. High-Level System Overview

Campus Connect is built as a modular monolithic web application powered by **Java 17** and **Spring Boot 3.2.5**. It serves both static frontend assets and JSON/WebSocket APIs from a single unified runtime, backed by a relational MySQL 8.0 / TiDB Cloud database.

```mermaid
flowchart TB
    subgraph ClientLayer["Client Layer (Browser)"]
        Landing["Public Pages (index.html, login.html)"]
        SPAShell["Authenticated SPA Shell (dashboard.html)"]
        Modules["ES6 Feature Modules (wall, chat, directory, events, jobs, admin, profile, bot)"]
        StompClient["SockJS + STOMP.js WebSocket Client"]
    end

    subgraph SecurityLayer["Spring Security & Middleware"]
        CORS["CORS Policy Filter"]
        JwtFilter["JwtAuthenticationFilter (Bearer Token Validation)"]
        SecContext["SecurityContextHolder (Stateless Principal)"]
    end

    subgraph ControllerLayer["Presentation Layer (REST & STOMP Controllers)"]
        RestControllers["REST Controllers (/api/auth, /api/users, /api/posts, /api/events, /api/jobs, /api/admin, /api/feedback, /api/notifications, /api/bot)"]
        WsController["WebSocket STOMP Controller (ChatController @MessageMapping)"]
        StompBroker["In-Memory STOMP Broker (/topic, /queue, /user)"]
    end

    subgraph ServiceLayer["Business Logic & Moderation Layer"]
        CoreServices["Domain Services (PostService, EventService, NotificationService, CustomUserDetailsService)"]
        ModServices["Moderation Services (ProfanityFilterService, UserModerationService)"]
        AsyncEmail["Async Email Broadcast (EmailService @Async)"]
    end

    subgraph DataLayer["Persistence & Storage Layer"]
        Repos["Spring Data JPA Repositories (9 Interfaces)"]
        MySQL[("MySQL 8.0 / TiDB Cloud Database")]
        Uploads[("Local / Container Volume (/uploads/**)")]
        SMTP["External SMTP Server (Gmail SMTP)"]
    end

    Landing -->|POST /api/auth/login| CORS
    SPAShell --> Modules
    Modules -->|HTTP Fetch + JWT Header| CORS
    StompClient <-->|WebSocket /ws| StompBroker
    StompClient -->|STOMP SEND /app/*| WsController
    WsController --> StompBroker

    CORS --> JwtFilter
    JwtFilter --> SecContext
    SecContext --> RestControllers

    RestControllers --> CoreServices
    RestControllers --> ModServices
    RestControllers --> AsyncEmail
    RestControllers --> Uploads
    WsController --> CoreServices
    CoreServices --> StompBroker

    CoreServices --> Repos
    ModServices --> Repos
    Repos --> MySQL
    AsyncEmail --> SMTP
```

---

## 2. Layered Backend Architecture

The Java backend (`com.bvicam.campusconnect`) follows a clean 5-layer separation of concerns:

```mermaid
flowchart LR
    A["1. Config & Security Layer\n(SecurityConfig, JwtAuthenticationFilter,\nWebSocketConfig, WebMvcConfig)"] --> B["2. Controller Layer\n(10 REST & STOMP Controllers)"]
    B --> C["3. Service Layer\n(7 Business, Moderation,\nand Async Services)"]
    C --> D["4. Repository Layer\n(9 Spring Data JPA\nRepositories)"]
    D --> E["5. Entity & Database Layer\n(12 JPA Entities / Enums\nin MySQL 8.0)"]
    B -.->|"Request / Response Mapping"| F["DTO Layer\n(8 Data Transfer Objects)"]
```

### Layer Responsibilities

| Layer | Package | Responsibilities |
| :--- | :--- | :--- |
| **Configuration & Security** | `com.bvicam.campusconnect.config`<br>`com.bvicam.campusconnect.util` | Configures stateless Spring Security, intercepts requests via `JwtAuthenticationFilter`, registers the `/ws` SockJS/STOMP broker, exposes `/uploads/**` for post images, and seeds initial demo data via `DataSeeder`. |
| **Presentation (Controllers)** | `com.bvicam.campusconnect.controller` | Exposes REST endpoints under `/api/**` and STOMP message handlers under `/app/**`. Handles HTTP status codes, role verification, and DTO conversion. |
| **Business Logic (Services)** | `com.bvicam.campusconnect.service` | Encapsulates transactional domain logic, bilingual profanity detection (`ProfanityFilterService`), isolated moderation flag transactions (`UserModerationService`), real-time notification dispatch (`NotificationService`), and `@Async` bulk email broadcasting (`EmailService`). |
| **Data Access (Repositories)** | `com.bvicam.campusconnect.repository` | Spring Data JPA interfaces providing CRUD operations, derived query methods, and custom JPQL queries (e.g., multi-criteria directory search and bidirectional chat history). |
| **Domain Models & DTOs** | `com.bvicam.campusconnect.entity`<br>`com.bvicam.campusconnect.dto` | JPA `@Entity` classes mapped to relational tables (with `@JsonIgnore` guards to prevent circular JSON serialization) and lightweight DTOs for clean API contracts. |

---

## 3. Authentication & Stateless Security Architecture

Campus Connect uses **stateless JWT authentication**. No HTTP server sessions are created (`SessionCreationPolicy.STATELESS`).

```mermaid
sequenceDiagram
    autonumber
    participant Client as Browser (api.js)
    participant Filter as JwtAuthenticationFilter
    participant Util as JwtUtil (HMAC-SHA256)
    participant UDS as CustomUserDetailsService
    participant SecCtx as SecurityContextHolder
    participant Ctrl as REST Controller

    Client->>Filter: HTTP Request with Header "Authorization: Bearer <jwt>"
    Filter->>Filter: Check if Header starts with "Bearer "
    Filter->>Util: extractUsername(jwt)
    Util-->>Filter: email (JWT Subject)
    Filter->>UDS: loadUserByUsername(email)
    UDS-->>Filter: UserDetails (email, passwordHash, ROLE_*)
    Filter->>Util: validateToken(jwt, userDetails)
    Util-->>Filter: true (Signature valid & not expired)
    Filter->>SecCtx: setAuthentication(UsernamePasswordAuthenticationToken)
    Filter->>Ctrl: filterChain.doFilter(request, response)
    Ctrl-->>Client: HTTP 200 JSON Response
```

### Route Access Matrix (`SecurityConfig.java`)
- **Public Routes (`permitAll`):**
  - Static frontend pages and assets: `/`, `/index.html`, `/login.html`, `/dashboard.html`, `/components/**`, `/css/**`, `/js/**`, `/images/**`, `/uploads/**`
  - Authentication endpoints: `/api/auth/**`
  - WebSocket handshake endpoint: `/ws/**`
  - GradBot FAQ endpoint: `/api/bot/**`
- **Protected Routes (`authenticated`):**
  - All other `/api/**` endpoints require a valid Bearer JWT in the `Authorization` header.
  - Role-specific operations (`ADMIN` vs. `ALUMNI` vs. `STUDENT`) are enforced at the controller/service level using the authenticated `Principal` or `SecurityContextHolder`.

---

## 4. Real-Time WebSocket & STOMP Architecture

Real-time features (Global Chat, 1-on-1 Direct Messaging, Live Typing Indicators, and Instant Notification Badges/Toasts) run over a single multiplexed **SockJS + STOMP** connection established when `dashboard.html` loads.

```mermaid
flowchart TB
    subgraph Browser["Browser Client (chat.js & notifications.js)"]
        SockJS["SockJS('/ws') + Stomp.over(socket)"]
        PubSub["Sub: /topic/public"]
        DMSub["Sub: /user/{myEmail}/queue/messages"]
        NotifSub["Sub: /user/{myEmail}/queue/notifications"]
        TypeSub["Sub: /topic/typing/{myEmail}"]
    end

    subgraph Server["Spring WebSocket Broker (WebSocketConfig & ChatController)"]
        AppPrefix["Application Prefix: /app"]
        ChatHandler["@MessageMapping('/chat.sendMessage')\n@MessageMapping('/chat.addUser')"]
        DMHandler["@MessageMapping('/chat.private')"]
        TypeHandler["@MessageMapping('/chat.typing')"]
        NotifSvc["NotificationService.createAndDispatch()"]
        SimpleBroker["In-Memory SimpleBroker\n(/topic, /queue, /user)"]
    end

    SockJS -->|"stompClient.send('/app/chat.sendMessage')"| AppPrefix
    SockJS -->|"stompClient.send('/app/chat.private')"| AppPrefix
    SockJS -->|"stompClient.send('/app/chat.typing')"| AppPrefix

    AppPrefix --> ChatHandler
    AppPrefix --> DMHandler
    AppPrefix --> TypeHandler

    ChatHandler -->|"Broadcast"| SimpleBroker
    DMHandler -->|"Save PrivateMessage to DB\n+ convertAndSendToUser()"| SimpleBroker
    DMHandler -->|"Trigger DM Alert"| NotifSvc
    NotifSvc -->|"Save Notification to DB\n+ convertAndSendToUser()"| SimpleBroker
    TypeHandler -->|"Relay typing status"| SimpleBroker

    SimpleBroker --> PubSub
    SimpleBroker --> DMSub
    SimpleBroker --> NotifSub
    SimpleBroker --> TypeSub
```

---

## 5. Frontend SPA Shell & Modal Hoisting Architecture

Instead of heavy frontend build toolchains, Campus Connect uses a fast, native **ES6 Module SPA Shell** pattern:

```mermaid
flowchart TD
    A["dashboard.html\n(Persistent Top Navbar, Left Sidebar,\nCenter #main-content, Right Global Chat,\nFloating GradBot)"] --> B["js/dashboard-main.js\n(Bootstraps Auth Check, WebSockets,\nand window.switchTab)"]
    B -->|"fetch('components/{tab}.html?v=timestamp')"| C["Inject HTML Fragment into #main-content"]
    C --> D["Modal Hoisting Step:\nMove any .modal inside #main-content\ndirectly to document.body"]
    D --> E["Execute Feature Module Initializer"]
    E --> F1["wall.js -> fetchPosts()"]
    E --> F2["messages.js / chat.js -> fetchRecentChats()"]
    E --> F3["directory.js -> runSearch()"]
    E --> F4["events.js -> fetchEvents()"]
    E --> F5["jobs.js -> fetchJobs()"]
    E --> F6["profile.js -> loadProfile()"]
    E --> F7["admin.js -> loadAdminModule()"]
```

### Why Modal Hoisting Matters
In `dashboard-main.js`, whenever `window.switchTab(tab)` injects a component fragment (`admin.html`, `events.html`, `directory.html`) into `#main-content`:
1. Any previously hoisted dynamic modals (`[data-cc-dynamic-modal="true"]`) and stale `.modal-backdrop` elements are disposed and removed.
2. Any `.modal` elements inside the newly loaded component are moved directly to `document.body` (`document.body.appendChild(modalEl)`).
3. This guarantees that component modals always share the top-level root stacking context with Bootstrap's `.modal-backdrop` (`z-index: 1050` vs. `.modal` `z-index: 1060`) and are never trapped inside `#main-content`.

---

## 6. Deployment & Container Topology

Campus Connect supports two primary deployment models:

### A. Production Cloud Topology (Render + TiDB Cloud)
- **Live URL:** [https://campus-connect-live.onrender.com](https://campus-connect-live.onrender.com)
- **Application Compute:** Hosted on **Render** using the root multi-stage [`Dockerfile`](../Dockerfile) (`eclipse-temurin:17-jre` runtime with container-tuned heap limits `-Xmx384m -Xms256m`).
- **Database:** Connected to **TiDB Cloud** (distributed MySQL-compatible serverless cluster) over TLS.

### B. Local Isolated Docker Compose Topology
Defined in [`docker-compose.yml`](../docker-compose.yml) so developers can run the entire stack locally without installing Java, Maven, or MySQL on their host machine:

```mermaid
flowchart LR
    HostBrowser["Host Browser\nhttp://localhost:8080"] -->|"Port 8080:8080"| AppContainer["Container: campus-connect-app\n(Eclipse Temurin JRE 17 + Spring Boot)"]
    AppContainer -->|"Internal Docker Network\nmysql:3306"| DbContainer[("Container: campus-connect-mysql\n(MySQL 8.0, Host Port 3307:3306)")]
    AppContainer <-->|"Bind Mount"| UploadsDir[("./uploads -> /app/uploads")]
    DbContainer <-->|"Named Volume"| DbVol[("campus_mysql_data")]
```
