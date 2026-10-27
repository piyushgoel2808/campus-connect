/**
 * ============================================================================
 * FILE: src/main/resources/static/js/modules/bot.js
 * PURPOSE: GradBot AI / Campus Intelligence Assistant floating widget controller.
 *
 * OVERVIEW:
 *   Powers the floating GradBot assistant drawer anchored in the bottom-right
 *   corner of `dashboard.html` (`#botWindow`). Supports toggling the chat
 *   window open/closed, clicking preset quick-question chips, typing custom
 *   questions, displaying a live typing spinner, rendering sanitized Markdown
 *   responses, verified source citation badges, and model metadata.
 *
 * API ENDPOINTS USED:
 *   - POST `/api/bot/ask` : Sends `{ question }` and receives `{ answer, model, citations }`.
 *
 * DOM ELEMENTS REFERENCED (in `dashboard.html`):
 *   - `#botWindow`   : Floating chat card container
 *   - `#botMessages` : Scrollable message history container
 *   - `#botInput`    : Text input field for user questions
 * ============================================================================
 */

import { send } from '../utils/api.js';

/**
 * Escapes unsafe HTML characters to guard against XSS injection.
 *
 * @param {string} str - Raw input text
 * @returns {string} Sanitized string
 */
function escapeHtml(str) {
    if (!str) return "";
    return String(str)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

/**
 * Validates that an external URL strictly adheres to HTTP or HTTPS schemes.
 * Prevents attribute breaking and dangerous protocols like `javascript:` or `data:`.
 *
 * @param {string} urlStr - Target URL string
 * @returns {boolean} True if valid http/https protocol
 */
function isSafeHttpUrl(urlStr) {
    if (!urlStr || typeof urlStr !== 'string') return false;
    try {
        const parsed = new URL(urlStr);
        return parsed.protocol === 'http:' || parsed.protocol === 'https:';
    } catch (e) {
        return false;
    }
}

/**
 * Formats Markdown tokens into clean HTML with dual-layer sanitization.
 * 1. Escapes raw HTML entities.
 * 2. Formats bold, italic, code blocks, bullet/numbered lists.
 * 3. Passes through DOMPurify if available in client runtime.
 *
 * @param {string} rawText - Unformatted model response
 * @returns {string} Safe HTML string
 */
function formatMarkdown(rawText) {
    if (!rawText) return "";

    // Step 1: Escape HTML characters first
    let escaped = escapeHtml(rawText);

    // Step 2: Code blocks (```code```)
    escaped = escaped.replace(/```(?:\w+)?\n?([\s\S]*?)```/g, '<pre class="bg-dark text-light p-2 rounded small overflow-auto my-2"><code>$1</code></pre>');

    // Step 3: Inline code (`code`)
    escaped = escaped.replace(/`([^`]+)`/g, '<code class="bg-light text-danger px-1 py-0.5 rounded small font-monospace">$1</code>');

    // Step 4: Headers (### Header)
    escaped = escaped.replace(/^### (.*$)/gim, '<strong class="d-block mt-2 mb-1 text-dark">$1</strong>');
    escaped = escaped.replace(/^## (.*$)/gim, '<h6 class="fw-bold mt-2 mb-1 text-primary">$1</h6>');

    // Step 5: Bold (**text**) & Italic (*text*)
    escaped = escaped.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
    escaped = escaped.replace(/\*([^\*]+)\*/g, '<em>$1</em>');

    // Step 6: Lists (* item, - item, • item)
    const lines = escaped.split('\n');
    const processedLines = [];
    let inList = false;

    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        const bulletMatch = line.match(/^\s*(?:[\*\-•]|\d+\.)\s+(.*)$/);

        if (bulletMatch) {
            if (!inList) {
                processedLines.push('<ul class="ps-3 mb-2" style="margin-top: 0.35rem;">');
                inList = true;
            }
            processedLines.push(`<li>${bulletMatch[1]}</li>`);
        } else {
            if (inList) {
                processedLines.push('</ul>');
                inList = false;
            }
            processedLines.push(line);
        }
    }
    if (inList) {
        processedLines.push('</ul>');
    }

    let html = processedLines.join('<br>').replace(/<br><ul/g, '<ul').replace(/<\/ul><br>/g, '</ul>');

    // Step 7: Dual-layer defense: sanitize via DOMPurify if loaded
    if (window.DOMPurify && typeof window.DOMPurify.sanitize === 'function') {
        html = window.DOMPurify.sanitize(html);
    }

    return html;
}

/**
 * Initializes the GradBot assistant by attaching `window.toggleBot` and
 * `window.askBot` handlers for use by `dashboard.html` inline `onclick` attributes.
 */
export function initBot() {
    /**
     * 1. Toggle Bot Window
     * Shows or hides `#botWindow` and auto-focuses `#botInput` when opened.
     */
    window.toggleBot = function() {
        const botWindow = document.getElementById("botWindow");
        if (!botWindow) return;

        const isVisible = botWindow.style.display === "flex";
        if (isVisible) {
            botWindow.style.display = "none";
        } else {
            botWindow.style.display = "flex";
            botWindow.style.flexDirection = "column";
            const input = document.getElementById("botInput");
            if (input) {
                setTimeout(() => input.focus(), 150);
            }
        }
    };

    /**
     * 2. Ask Bot Function (handles typing and quick prompt clicks)
     * Sends either a clicked `presetQuestion` chip string or the current text
     * in `#botInput` to `POST /api/bot/ask`, then renders GradBot's reply.
     *
     * @param {string} [presetQuestion] - Optional pre-filled prompt from quick-action buttons.
     */
    window.askBot = async function(presetQuestion) {
        const input = document.getElementById("botInput");
        const messagesArea = document.getElementById("botMessages");
        if (!input || !messagesArea) return;

        const question = (presetQuestion || input.value).trim();
        if (!question) return;

        // Remove initial welcome placeholder if present
        const placeholder = messagesArea.querySelector(".system-placeholder");
        if (placeholder) placeholder.remove();

        // Append user question bubble and clear input field
        appendBotMessage(question, "user");
        input.value = "";

        // Show temporary typing indicator while awaiting backend response
        const typingId = "bot-typing-" + Date.now();
        const typingDiv = document.createElement("div");
        typingDiv.id = typingId;
        typingDiv.className = "small text-muted fst-italic px-2 py-1";
        typingDiv.innerHTML = `<i class="fas fa-circle-notch fa-spin me-1 text-primary"></i> GradBot is searching campus knowledge & reasoning...`;
        messagesArea.appendChild(typingDiv);
        messagesArea.scrollTop = messagesArea.scrollHeight;

        try {
            const response = await send('/bot/ask', 'POST', { question: question });
            const data = await response.json();

            const typingEl = document.getElementById(typingId);
            if (typingEl) typingEl.remove();

            const answer = data.answer || "I couldn't process that question. Try asking about placements, batch, or events.";
            appendBotMessage(answer, "bot", data.citations, data.model);

        } catch (e) {
            console.error("GradBot error:", e);
            const typingEl = document.getElementById(typingId);
            if (typingEl) typingEl.remove();
            appendBotMessage("⚠️ Could not reach GradBot right now. Please ensure you are logged in and Ollama is running.", "bot");
        }
    };
}

/**
 * Creates and appends a styled chat bubble to `#botMessages` and scrolls to the bottom.
 *
 * @param {string} text             - Message text content to display.
 * @param {'user'|'bot'} sender     - Whether the bubble is from the user or GradBot.
 * @param {Array}  [citations=[]]   - Source citations linking to notices or URLs.
 * @param {string} [model=null]     - LLM identifier (e.g. gemma4:e4b).
 */
function appendBotMessage(text, sender, citations = [], model = null) {
    const messagesArea = document.getElementById("botMessages");
    if (!messagesArea) return;

    const div = document.createElement("div");
    if (sender === "user") {
        // Right-aligned brand gradient bubble for user prompts
        div.className = "message my-message align-self-end";
        div.style.background = "var(--cc-gradient-brand)";
        div.style.color = "#ffffff";
        div.style.borderRadius = "14px";
        div.style.borderBottomRightRadius = "3px";
        div.style.padding = "0.65rem 0.95rem";
        div.style.maxWidth = "84%";
        div.style.fontSize = "0.9rem";
        div.textContent = text;
    } else {
        // Left-aligned white card bubble for GradBot responses
        div.className = "message other-message align-self-start bot-response-card";
        div.style.background = "#ffffff";
        div.style.border = "1px solid var(--cc-border)";
        div.style.borderRadius = "14px";
        div.style.borderBottomLeftRadius = "3px";
        div.style.padding = "0.75rem 1rem";
        div.style.maxWidth = "92%";
        div.style.fontSize = "0.88rem";
        div.style.boxShadow = "var(--cc-shadow-sm)";

        // 1. Header with Avatar & Badge
        const header = document.createElement("div");
        header.className = "d-flex align-items-center justify-content-between mb-2";
        header.innerHTML = `
            <div class="d-flex align-items-center gap-1 text-primary fw-bold" style="font-size: 0.8rem;">
                <i class="fas fa-robot"></i> GradBot
                <span class="badge bg-primary-subtle text-primary border border-primary-subtle ms-1" style="font-size: 0.65rem;">AI Verified</span>
            </div>
        `;
        div.appendChild(header);

        // 2. Formatted Markdown Body
        const body = document.createElement("div");
        body.className = "bot-markdown-content";
        body.innerHTML = formatMarkdown(text);
        div.appendChild(body);

        // 3. Source Citations (Rendered safely using DOM attribute assignments)
        if (citations && citations.length > 0) {
            const citationsContainer = document.createElement("div");
            citationsContainer.className = "bot-citations-box mt-2 pt-2 border-top d-flex flex-wrap gap-1 align-items-center";

            const label = document.createElement("span");
            label.className = "text-muted small fw-bold me-1";
            label.innerHTML = '<i class="fas fa-bookmark me-1 text-primary"></i> Sources:';
            citationsContainer.appendChild(label);

            citations.forEach(c => {
                if (c.sourceUrl && isSafeHttpUrl(c.sourceUrl)) {
                    // Safe external HTTP/HTTPS link constructed via DOM property assignment
                    const link = document.createElement("a");
                    link.href = new URL(c.sourceUrl).href;
                    link.target = "_blank";
                    link.rel = "noopener noreferrer";
                    link.className = "badge bg-light text-primary border text-decoration-none d-inline-flex align-items-center gap-1 bot-citation-pill";
                    link.innerHTML = `<i class="fas fa-external-link-alt" style="font-size:0.65rem;"></i> <span>${escapeHtml(c.title || 'Source')}</span>`;
                    if (c.similarity) {
                        const score = document.createElement("span");
                        score.className = "text-muted ms-1";
                        score.style.fontSize = "0.65rem";
                        score.textContent = `(${Math.round(c.similarity * 100)}%)`;
                        link.appendChild(score);
                    }
                    citationsContainer.appendChild(link);
                } else {
                    // Safe notice/document badge
                    const badge = document.createElement("span");
                    badge.className = "badge bg-light text-dark border d-inline-flex align-items-center gap-1 bot-citation-pill";
                    const icon = c.category === 'NOTICE' ? 'fa-bell text-danger' :
                                 c.category === 'SYLLABUS' ? 'fa-book text-info' : 'fa-file-alt text-secondary';
                    badge.innerHTML = `<i class="fas ${icon}" style="font-size:0.7rem;"></i> <span>${escapeHtml(c.title || 'Campus Document')}</span>`;
                    if (c.similarity) {
                        const score = document.createElement("span");
                        score.className = "text-muted ms-1";
                        score.style.fontSize = "0.65rem";
                        score.textContent = `(${Math.round(c.similarity * 100)}%)`;
                        badge.appendChild(score);
                    }
                    citationsContainer.appendChild(badge);
                }
            });

            div.appendChild(citationsContainer);
        }

        // 4. Model metadata footer
        const footer = document.createElement("div");
        footer.className = "d-flex justify-content-between align-items-center mt-2 pt-1 border-top border-light text-muted";
        footer.style.fontSize = "0.7rem";
        footer.innerHTML = `
            <span><i class="fas fa-microchip me-1 text-secondary"></i> ${escapeHtml(model || 'gemma4:e4b')}</span>
            <span class="text-success fw-semibold"><i class="fas fa-shield-alt me-1"></i> BVICAM AI RAG</span>
        `;
        div.appendChild(footer);
    }

    messagesArea.appendChild(div);
    messagesArea.scrollTop = messagesArea.scrollHeight;
}
