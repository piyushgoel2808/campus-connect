# TICKET-AI-001: [Tracer Bullet 1] Ollama Connectivity & Core LLM Chat Slice

- **Status:** IN_PROGRESS
- **Assignee:** Antigravity / spring-boot-engineer
- **Type:** Core Architecture / Vertical Slice
- **Estimate:** 2.5h
- **Blocked By:** None
- **Blocks:** TICKET-AI-002, TICKET-AI-005

## Context & User Story
As a user (Student, Alumni, Admin) of Campus Connect,
I want to ask questions to GradBot and receive intelligent responses from the local `gemma4:e4b` model running in Ollama,
So that I get immediate AI assistance with graceful fallback if the local model is offline.

## Scope of Work
1. Add configuration in `application.properties`:
   - `ollama.base-url=http://localhost:11434`
   - `ollama.chat-model=gemma4:e4b`
   - `ollama.embedding-model=nomic-embed-text`
   - `ollama.timeout-seconds=60`
2. Create `OllamaClientService` using constructor injection and `java.net.http.HttpClient`:
   - `generateChatResponse(String systemPrompt, String userQuery)`
   - `generateEmbedding(String text)`
   - Catch connection and timeout exceptions; return fallback message if unreachable.
3. Update `BotController.java` (`POST /api/bot/ask`) to delegate to `OllamaClientService`.
4. Create DTO records `OllamaChatRequest`, `OllamaChatResponse`, `OllamaEmbeddingResponse`.

## TDD Implementation Plan
1. Write failing integration test: `src/test/java/com/bvicam/campusconnect/controller/BotControllerIntegrationTest.java`
   - Verifies `POST /api/bot/ask` receives user prompt, calls Ollama (or mocked Ollama response), and returns `{ "answer": "..." }`.
   - Verify test fails.
2. Implement minimal code:
   - Configuration properties
   - `OllamaClientService`
   - Inject into `BotController`
3. Verify test passes.
4. Run code review.
