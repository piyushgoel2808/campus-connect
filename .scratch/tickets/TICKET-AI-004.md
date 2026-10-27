# TICKET-AI-004: [Tracer Bullet 3] BVICAM Web Ingestion & Admin URL Scraper Slice

- **Status:** BACKLOG
- **Type:** Full-Stack Feature / Vertical Slice
- **Estimate:** 2.5h
- **Blocked By:** TICKET-AI-003
- **Blocks:** TICKET-AI-005

## Context & User Story
As an Administrator,
I want to provide official `bvicam.in` URLs to scrape and index into the RAG knowledge base,
So that students and alumni can query official curriculum, faculty, and college info.

## Scope of Work
1. Add Jsoup (`org.jsoup:jsoup:1.17.2`) to `pom.xml`.
2. Implement `KnowledgeIngestionService.scrapeAndIndexUrl(url, title, category, currentUser)`.
3. Expose `POST /api/admin/knowledge/scrape-url` in `AdminKnowledgeController`.
4. Add URL scraper form in `admin.html` and wire handler in `admin.js`.
