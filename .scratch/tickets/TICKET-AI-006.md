# TICKET-AI-006: [Tracer Bullet 5] GradBot UI Polish, Markdown Citations & Quick Prompts

- **Status:** DONE
- **Type:** Frontend UX / Final Polish
- **Estimate:** 2h
- **Blocked By:** TICKET-AI-005
- **Blocks:** None

## Context & User Story
As a user (Student, Alumni, Admin),
I want GradBot to render formatted markdown, display clickable citation badges, and offer quick prompt suggestions,
So that I have an intuitive, pleasant conversational experience.

## Scope of Work
1. Update `appendBotMessage()` in `bot.js` to render Markdown and source citation pills:
   - Security: Sanitize all rendered HTML using DOMPurify before inserting into `#botMessages`.
   - Security: Construct citation links using safe DOM attribute assignments (`a.href = parsedUrl.href`) or strict attribute escaping rather than raw template interpolation.
   - Security: Validate citation links with an explicit `http:` / `https:` allowlist and reject any scheme violations (preventing `javascript:` or `data:` URI attacks).
2. Add quick prompt chips in `dashboard.html`.
3. Style chat bubbles in `components.css`.

## Acceptance Criteria
- [ ] Model markdown output is sanitized and stripped of script tags or malicious event handlers.
- [ ] Citations render with verified HTTP/HTTPS anchors constructed via DOM element properties.
- [ ] Regression test verifies that attribute-breaking URLs like `https://example.com/" onmouseover="..."` cannot break out into event handlers.
