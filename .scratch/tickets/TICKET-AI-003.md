# TICKET-AI-003: [Tracer Bullet 2] Notice Ingestion & Admin Document Upload Slice

- **Status:** DONE
- **Type:** Full-Stack Feature / Vertical Slice
- **Estimate:** 3h
- **Blocked By:** TICKET-AI-002
- **Blocks:** TICKET-AI-004, TICKET-AI-005

## Context & User Story
As an Administrator,
I want to upload PDF or text circulars in the Admin Workspace,
So that GradBot indexes the content and can immediately answer questions based on the new notice.

## Scope of Work
1. Add Apache PDFBox (`org.apache.pdfbox:pdfbox:3.0.1`) to `pom.xml`.
2. Implement `TextChunker` (~600 chars, 100 char overlap).
3. Implement `KnowledgeIngestionService.ingestFile(...)`.
4. Create `AdminKnowledgeController` (`POST /api/admin/knowledge/upload`, `GET /api/admin/knowledge/documents`, `DELETE /api/admin/knowledge/documents/{id}`).
5. Add `#admin-knowledge` sub-tab in `admin.html` and wire `admin.js`.
