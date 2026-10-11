# 📚 Campus Connect (GradLink | BVICAM) — Documentation Hub

> **🌐 Live Production Application:** [https://campus-connect-live.onrender.com](https://campus-connect-live.onrender.com)

Welcome to the technical documentation suite for **Campus Connect (GradLink)** — a real-time campus networking, career referral, and community management platform built with **Spring Boot 3.2.5 (Java 17)**, **Spring Security (JWT)**, **Spring WebSockets (STOMP/SockJS)**, **MySQL / TiDB Cloud**, and **Vanilla ES6 Modules**.

---

## 📖 Documentation Index

| Document | Description |
| :--- | :--- |
| [**1. System Architecture (`architecture.md`)**](architecture.md) | High-level system architecture, 5-layer backend design, stateless JWT filter chain, STOMP WebSocket message broker topology, SPA modal hoisting, and Docker/Render deployment architecture. |
| [**2. Codebase Directory & Reference (`codebase-directory.md`)**](codebase-directory.md) | Complete repository directory tree and file-by-file reference covering every Java class, REST/STOMP endpoint, JPA repository query, frontend ES6 module, HTML component, and CSS file. |
| [**3. End-to-End Data Flows (`dataflow.md`)**](dataflow.md) | Detailed sequence diagrams and flowcharts for Authentication, Campus Wall Posting & Bilingual Profanity Moderation, Real-Time 1-on-1 Chat, Directory Search, Event RSVPs, Jobs & Referrals, and Admin Bulk Upload. |
| [**4. Database Schema & ERD (`database-schema.md`)**](database-schema.md) | Complete Entity-Relationship Diagram (`erDiagram`) and table-by-table column specifications for all 11 relational tables (`users`, `departments`, `posts`, `comments`, `event`, `event_participants`, `jobs`, `private_messages`, `chat_messages`, `notifications`, `feedback`). |
| [**5. Features & RBAC Guide (`features.md`)**](features.md) | Role-Based Access Control (`STUDENT` / `ALUMNI` / `ADMIN`) matrix and deep dive into all 9 feature modules. |
