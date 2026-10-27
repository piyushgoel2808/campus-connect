# TICKET-AI-005: [Tracer Bullet 4] Hybrid RAG Retrieval & Live Domain Context Synthesis

- **Status:** BACKLOG
- **Type:** Backend Core Intelligence / Vertical Slice
- **Estimate:** 3.5h
- **Blocked By:** TICKET-AI-001, TICKET-AI-002, TICKET-AI-004
- **Blocks:** TICKET-AI-006

## Context & User Story
As a user (Student, Alumni, Admin),
I want GradBot to answer questions using vector search over notices/BVICAM web data combined with real-time MySQL database queries (Events, Jobs, Alumni),
So that I get factual, comprehensive, and up-to-date responses with citations.

## Scope of Work
1. Implement `CampusAiService`:
   - Compute query embedding via `OllamaClientService.generateEmbedding()`.
   - Vector similarity search across `DocumentChunkRepository` (top-K = 4).
   - Dynamic real-time fetch from `EventRepository`, `JobRepository`, and `UserRepository`.
   - Prompt engineering and synthesis via `OllamaClientService.generateChatResponse()`.
2. Update `BotController.java` to return `BotAskResponse` with citations.
