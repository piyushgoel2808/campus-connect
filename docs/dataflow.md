# 🔄 End-to-End Data Flows — Campus Connect (GradLink)

> **Live Deployment:** [https://campus-connect-live.onrender.com](https://campus-connect-live.onrender.com)

This document traces the complete end-to-end lifecycle of user actions in **Campus Connect**, from browser DOM events through the Spring Security filter chain, REST/STOMP controllers, business/moderation services, JPA repositories, and MySQL database, back to real-time UI updates.

---

## 1. User Login & JWT Session Initialization

When a student, alumnus, or administrator signs in via `login.html`:

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant LoginUI as login.html + app.js
    participant AuthCtrl as AuthController (/api/auth/login)
    participant AuthMgr as AuthenticationManager
    participant UDS as CustomUserDetailsService
    participant DB as MySQL (users)
    participant JwtUtil as JwtUtil
    participant DashUI as dashboard.html + dashboard-main.js

    User->>LoginUI: Enter email & password, click "Sign In"
    LoginUI->>AuthCtrl: POST /api/auth/login {email, password}
    AuthCtrl->>AuthMgr: authenticate(UsernamePasswordAuthenticationToken)
    AuthMgr->>UDS: loadUserByUsername(email)
    UDS->>DB: SELECT * FROM users WHERE email = ?
    DB-->>UDS: User entity (passwordHash, role)
    UDS-->>AuthMgr: UserDetails (BCrypt hash + ROLE_*)
    AuthMgr->>AuthMgr: BCryptPasswordEncoder.matches(raw, hash)
    AuthMgr-->>AuthCtrl: Authenticated token
    AuthCtrl->>JwtUtil: generateToken(email)
    JwtUtil-->>AuthCtrl: Signed JWT (10h expiry)
    AuthCtrl-->>LoginUI: 200 OK AuthResponse {token, id, name, role}
    LoginUI->>LoginUI: Save jwt_token, user_email, user_role, user_name in localStorage
    LoginUI->>DashUI: Redirect to dashboard.html
    DashUI->>DashUI: Connect WebSocket (/ws), load Notifications, switchTab('wall')
```

---

## 2. Campus Wall Post Creation, Photo Upload & Bilingual Profanity Moderation

When a user publishes a post on the Campus Wall (`wall.html` + `wall.js`), the flow supports optional multipart image upload followed by automated bilingual profanity screening (`ProfanityFilterService`) and isolated user flagging (`UserModerationService` with `Propagation.REQUIRES_NEW`):

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant WallJS as wall.js
    participant PostCtrl as PostController (/api/posts)
    participant PostSvc as PostService
    participant Profanity as ProfanityFilterService
    participant ModSvc as UserModerationService (REQUIRES_NEW)
    participant DB as MySQL (posts, users)

    alt User selected a local photo file
        User->>WallJS: Choose image file (< 5MB)
        WallJS->>PostCtrl: POST /api/posts/upload (MultipartFile)
        PostCtrl->>PostCtrl: Save UUID filename to ./uploads/
        PostCtrl-->>WallJS: 200 OK {imageUrl: "/uploads/uuid.png"}
    end

    User->>WallJS: Click "Publish Post"
    WallJS->>PostCtrl: POST /api/posts {content, imageUrl}
    PostCtrl->>PostSvc: createPost(content, imageUrl, email)
    PostSvc->>Profanity: containsProfanity(content)

    alt Profanity Detected (English or Hindi/Hinglish)
        Profanity-->>PostSvc: true
        PostSvc->>ModSvc: flagUser(author.id) [Independent Transaction]
        ModSvc->>DB: UPDATE users SET is_flagged=true, flag_count=flag_count+1
        PostSvc-->>PostCtrl: throw IllegalArgumentException("Post blocked...")
        PostCtrl-->>WallJS: 400 Bad Request {"error": "Post blocked..."}
        WallJS->>User: Display red moderation alert banner
    else Clean Content
        Profanity-->>PostSvc: false
        PostSvc->>DB: INSERT INTO posts (content, image_url, user_id, created_at)
        DB-->>PostSvc: Saved Post entity
        PostSvc-->>PostCtrl: Post JSON
        PostCtrl-->>WallJS: 200 OK Post JSON
        WallJS->>WallJS: Prepend post card to feed
    end
```

---

## 3. Real-Time 1-on-1 Direct Messaging & Live Notifications (STOMP WebSockets)

Private messaging combines persistent relational storage (`private_messages` table) with instant push delivery over STOMP queues:

```mermaid
sequenceDiagram
    autonumber
    actor Sender as Sender Browser (chat.js)
    participant Broker as Spring STOMP Broker (/ws)
    participant ChatCtrl as ChatController (@MessageMapping)
    participant MsgRepo as PrivateMessageRepository
    participant NotifSvc as NotificationService
    actor Receiver as Receiver Browser (chat.js + notifications.js)

    Sender->>ChatCtrl: STOMP SEND /app/chat.typing {senderName, receiverName}
    ChatCtrl->>Broker: convertAndSend("/topic/typing/" + receiverEmail)
    Broker-->>Receiver: Show "User is typing..." indicator for 2s

    Sender->>ChatCtrl: STOMP SEND /app/chat.private {receiverName, content}
    ChatCtrl->>ChatCtrl: Override senderEmail from authenticated Principal
    ChatCtrl->>MsgRepo: save(PrivateMessage)
    ChatCtrl->>Broker: convertAndSendToUser(receiverEmail, "/queue/messages", payload)
    ChatCtrl->>Broker: convertAndSendToUser(senderEmail, "/queue/messages", payload)
    Broker-->>Sender: Append message bubble to Sender's DM modal
    Broker-->>Receiver: Append message bubble to Receiver's DM modal (or show #msgBadge)

    ChatCtrl->>NotifSvc: createAndDispatch(receiverEmail, MESSAGE, "New message...", content)
    NotifSvc->>NotifSvc: Check receiver.notifyMessages == true
    NotifSvc->>Broker: convertAndSendToUser(receiverEmail, "/queue/notifications", dto)
    Broker-->>Receiver: Increment red Bell Badge & pop up live Toast notification
```

---

## 4. Directory Search & Multi-Criteria Filtering

When a user types in the Directory search box or selects a Role (`STUDENT` / `ALUMNI`) or Graduation Batch (`2020`–`2025`):

```mermaid
flowchart LR
    A["User types keyword or changes\nRole / Batch dropdown in directory.html"] -->|"300ms Debounce"| B["directory.js: runSearch()"]
    B -->|"GET /api/users/search?role=ALUMNI&batch=2024&q=Java"| C["UserController.searchDirectory()"]
    C -->|"Convert role String -> Role Enum"| D["UserRepository.searchUsers(role, batch, q)"]
    D -->|"JPQL Query on MySQL users + departments"| E[("MySQL Database")]
    E -->|"List<User>"| C
    C -->|"Map to List<UserProfileDto>\n(includes batchYear, enrollmentNumber, departmentCode)"| B
    B -->|"Store in directoryUsersMap & render rows"| F["Click row -> openUserProfile(u.id)\nOpens #userProfileModal"]
```

---

## 5. Event Creation, RSVP Toggling & Participant Inspection

```mermaid
sequenceDiagram
    autonumber
    actor Admin as Admin Browser (events.js)
    actor Student as Student Browser (events.js)
    participant EvtCtrl as EventController (/api/events)
    participant NotifSvc as NotificationService
    participant DB as MySQL (event, event_participants)

    Admin->>EvtCtrl: POST /api/events {title, description, location, dateTime}
    EvtCtrl->>DB: INSERT INTO event (...)
    EvtCtrl->>NotifSvc: Broadcast EVENT notification to all users (notifyEvents == true)
    EvtCtrl-->>Admin: 200 OK Saved Event

    Student->>EvtCtrl: GET /api/events
    EvtCtrl->>DB: Fetch events + check if currentUser in event.participants
    EvtCtrl-->>Student: List<EventDTO> {attending: false, participantCount: 0}

    Student->>EvtCtrl: POST /api/events/{id}/rsvp
    EvtCtrl->>DB: Add Student to event_participants join table
    EvtCtrl->>NotifSvc: Send RSVP confirmation notification to Student
    EvtCtrl-->>Student: 200 OK (UI button changes to green "Going" & count increments)

    Admin->>EvtCtrl: GET /api/events/{id}/participants
    EvtCtrl->>DB: SELECT participants FROM event_participants WHERE event_id = ?
    EvtCtrl-->>Admin: Set<User> rendered in #participantsModal
```

---

## 6. Jobs & Referrals Lifecycle (Admin & Alumni)

Both **Alumni** and **Admins** can post, edit, and delete career opportunities and internal referrals from either the **Jobs & Referrals** tab or the **Admin Workspace -> Jobs & Referrals** sub-tab:

```mermaid
flowchart TD
    A["Admin or Alumni clicks\n'+ Post Job / Referral'"] --> B["Fills #postJobModal\n(Title, Company, Location, Description, URL/Email)"]
    B -->|"POST /api/jobs"| C["JobController.postJob()"]
    C -->|"Verify role is ADMIN or ALUMNI"| D["Save Job entity to MySQL (jobs)"]
    D --> E["Loop through all STUDENT users:\nNotificationService.createAndDispatch(JOB)"]
    E --> F["Refresh Job Cards (jobs.js)\n& Admin Jobs Table (admin.js)"]
    F --> G["Admin or Poster clicks 'Edit' on a Job"]
    G -->|"Opens #editJobModal -> PUT /api/jobs/{id}"| H["JobController.updateJob()\nUpdates Title, Company, Location, Description, Link"]
```

---

## 7. Admin Bulk User Onboarding & Asynchronous Email Broadcast

### A. Bulk Excel / CSV User Import
1. Admin selects a `.xlsx` or `.csv` file in **Admin Workspace -> Manage Users** and clicks **Upload `.xlsx / .csv`**.
2. `admin.js` sends a ` multipart/form-data` request to `POST /api/admin/bulk-upload`.
3. `AdminController.bulkUpload` detects the file extension:
   - `.xlsx`: Parsed with **Apache POI** (`XSSFWorkbook`).
   - `.csv`: Parsed line-by-line via `BufferedReader`.
4. Each row resolves or creates the `Department` (`MCA` / `BJMC`), checks for duplicate email addresses, hashes the default password (`Bvicam@2025`) with `BCryptPasswordEncoder`, sets `passwordChanged = false`, and saves all new users in a single batch (`userRepository.saveAll(usersToSave)`).

### B. Asynchronous Campus-Wide Email Broadcast
1. Admin enters a Subject and Message in **Admin Workspace -> Broadcast Email** and clicks **Send Broadcast to Community**.
2. `POST /api/admin/broadcast` collects all registered user email addresses from `UserRepository` and invokes `emailService.sendBroadcastEmail(emails, subject, message)`.
3. Because `sendBroadcastEmail` is annotated with `@Async`, Spring immediately returns `200 OK ("✅ Broadcast initiated for N users.")` to the browser while dispatching the SMTP emails on a background worker thread.
