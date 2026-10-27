# TICKET-AI-002: Knowledge Base Domain Entities & Vector Storage

- **Status:** DONE
- **Type:** Domain & Database Layer
- **Estimate:** 2h
- **Blocked By:** TICKET-AI-001
- **Blocks:** TICKET-AI-003, TICKET-AI-005

## Context & User Story
As an application architect,
I want persistent storage for ingested documents, chunk segments, and embedding vectors in MySQL/TiDB,
So that RAG similarity searches can be executed efficiently.

## Scope of Work
1. Create `KnowledgeDocument` entity (`id`, `title`, `category`, `sourceType`, `sourceUrl`, `originalFileName`, `chunkCount`, `uploadedBy`, `createdAt`, `updatedAt`).
2. Create `DocumentChunk` entity (`id`, `document_id` FK with cascade delete, `chunkIndex`, `chunkText` as `@Lob`, `embeddingJson` as `@Lob LONGTEXT`).
3. Create enums `DocumentCategory` (`NOTICE`, `SYLLABUS`, `BVICAM_WEB`, `GENERAL`) and `SourceType` (`FILE_UPLOAD`, `WEB_URL`, `MANUAL_TEXT`).
4. Create `KnowledgeDocumentRepository` and `DocumentChunkRepository` with index on `document_id` and category pre-filter query.
5. Implement `VectorUtil.cosineSimilarity(double[] v1, double[] v2)`.

## Scalability Contract & Measurement Basis
- Embedding Model: 768-dimensional normalized vectors from `nomic-embed-text`.
- Storage Format: `@Lob LONGTEXT` JSON array in `document_chunks` table (~7 KB JSON text per chunk, ~70 MB for full 10,000 chunk corpus).
- In-Memory Cosine Ranking: Cached as `float[768]` in heap (~30 MB for 10,000 chunks). On Apple Silicon (M-series) / multi-core Linux, scanning 10,000 768-dim vectors takes < 25ms CPU time.
- Category Pre-filtering: Candidate chunks are pre-filtered by indexed `category` / `document_id` so typical production queries rank < 1,000 candidates (< 3 MB in heap, < 5ms CPU time).
