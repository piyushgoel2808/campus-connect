# 🎓 Campus Connect
A unified, real-time campus networking platform designed to bridge the communication gap between students, alumni, and college administration. Built with Spring Boot and WebSockets, this platform fosters a thriving academic community through real-time chat, event management, and career opportunities.

## 🎯 Aim & Objective
The primary aim of Campus Connect is to provide an exclusive, secure, and centralized digital ecosystem for the college community. It eliminates the reliance on fragmented third-party social apps by offering a dedicated space where:

- Students can seek guidance, find jobs, and participate in campus life.
- Alumni can easily give back, share industry insights, and network with peers.
- Administrators can effortlessly manage events, broadcast announcements, and onboard users efficiently.

## ✨ Key Features
### 1. 🔐 Authentication & Role Management
- Secure Access: JWT (JSON Web Token) based authentication.
- Role-Based Access Control (RBAC): Distinct privileges for ADMIN, STUDENT, and ALUMNI.
- Bulk Onboarding: Admins can instantly upload hundreds of student/alumni accounts using Excel/CSV parsing.

### 2. 💬 Real-Time Communication (WebSockets)
- Global Chat (Public): A live campus-wide chat room for general discussions and announcements.
- Direct Messaging (Private): 1-on-1 private messaging between any two users with real-time notifications and persistent chat history.
- Powered by Spring STOMP WebSockets and SockJS.

### 3. 📅 Event Management & RSVP
- Event Dashboard: View upcoming seminars, fests, and alumni meets.
- Interactive RSVP: Users can instantly confirm their attendance with a single click.
- Admin Controls: Admins can create, edit, and delete events, as well as view a real-time list of attending participants.

### 4. 🤝 Community & Career Building
- User Directory: A searchable database of all registered students and alumni.
- Job & Internship Board: A dedicated space to post and discover career opportunities.
- Memory Wall: A social feed where users can share achievements, campus photos, and memories.
- Feedback System: A direct channel for students to send suggestions to the administration.

## 🛠️ Technology Stack
**Backend**
- Java 17 + Spring Boot 3.x (REST APIs & Application Logic)
- Spring Security & JWT (Authentication)
- Spring WebSockets & STOMP (Real-time Messaging)
- Spring Data JPA / Hibernate (ORM)

**Frontend**
- HTML5, CSS3, Vanilla JavaScript (ES6+)
- Bootstrap 5 (Responsive UI/UX)
- SockJS & Stomp.js (WebSocket Client)

**Database & Deployment**
- TiDB Cloud (Distributed, MySQL-compatible cloud database)
- Render (Cloud Application Hosting via Dockerfile)
- Maven (Dependency Management)

## 🗄️ Database Architecture
The system relies on a relational database architecture with the following core entities:
- User: Stores credentials, roles, and profile data.
- Event & event_participants: Manages event details and Many-to-Many RSVP relationships.
- PrivateMessage: Stores sender, receiver, and timestamps for 1-on-1 chats.
- Post & Job: Stores user-generated content for the Memory Wall and Career board.
- Feedback: Captures user queries and suggestions.

## 🚀 Setup & Installation

### Option 1: Docker Compose (Recommended — Zero Local Dependencies)
The project includes an isolated multi-container Docker setup ([`Dockerfile`](Dockerfile) + [`docker-compose.yml`](docker-compose.yml)) that bundles **Eclipse Temurin JDK 17**, **Maven 3.9**, and **MySQL 8.0** so nothing needs to be installed on your host machine.

```bash
# Build and start both MySQL and the Spring Boot application in the background
docker compose up --build -d

# Tail application logs
docker compose logs -f app

# Stop containers (preserving database volume)
docker compose down
```

- **Web App URL:** `http://localhost:8080`
- **Isolated Containers:**
  - `campus-connect-app` — Spring Boot 3.2.5 + JDK 17 (`localhost:8080 -> 8080`)
  - `campus-connect-mysql` — MySQL 8.0 (`localhost:3307 -> 3306`, persistent volume `campus_mysql_data`)

### Option 2: Local Native Setup
#### Prerequisites
- Java Development Kit (JDK) 17
- Maven 3.8+
- MySQL Server 8.0+

```bash
# Run with Maven Wrapper
./mvnw spring-boot:run
```

---

## 🔑 Default Seeded Accounts
On first startup, [`DataSeeder`](src/main/java/com/bvicam/campusconnect/config/DataSeeder.java) automatically seeds the database with default departments (`MCA`, `BJMC`) and demo accounts:

| Role | Email | Password | Capabilities |
| :--- | :--- | :--- | :--- |
| **Admin** | `admin@bvicam.in` | `admin123` | Full user CRUD, bulk `.xlsx`/`.csv` onboarding, post moderation, event creation/editing, job/referral posting, email broadcast, feedback review |
| **Alumni** | `alumni@bvicam.in` | `1234` | Post jobs & referrals, directory search, wall posts, event RSVP, global & 1-on-1 chat |
| **Student** | `student@bvicam.in` | `1234` | Browse & apply to jobs, directory search, wall posts, event RSVP, global & 1-on-1 chat |

