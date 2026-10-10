/**
 * ============================================================================
 * FILE: src/main/resources/static/js/modules/bot.js
 * PURPOSE: GradBot AI / FAQ Assistant floating widget controller.
 *
 * OVERVIEW:
 *   Powers the floating GradBot assistant drawer anchored in the bottom-right
 *   corner of `dashboard.html` (`#botWindow`). Supports toggling the chat
 *   window open/closed, clicking preset quick-question chips, typing custom
 *   questions, displaying a live typing spinner, and rendering conversation
 *   bubbles inside `#botMessages`.
 *
 * API ENDPOINTS USED:
 *   - POST `/api/bot/ask` : Sends `{ question }` and receives `{ answer }`.
 *
 * DOM ELEMENTS REFERENCED (in `dashboard.html`):
 *   - `#botWindow`   : Floating chat card container
 *   - `#botMessages` : Scrollable message history container
 *   - `#botInput`    : Text input field for user questions
 * ============================================================================
 */

import { send } from '../utils/api.js';

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
        typingDiv.innerHTML = `<i class="fas fa-circle-notch fa-spin me-1 text-primary"></i> GradBot is typing...`;
        messagesArea.appendChild(typingDiv);
        messagesArea.scrollTop = messagesArea.scrollHeight;

        try {
            const response = await send('/bot/ask', 'POST', { question: question });
            const data = await response.json();

            const typingEl = document.getElementById(typingId);
            if (typingEl) typingEl.remove();

            const answer = data.answer || "I couldn't process that question. Try asking about placements, batch, or events.";
            appendBotMessage(answer, "bot");

        } catch (e) {
            console.error("GradBot error:", e);
            const typingEl = document.getElementById(typingId);
            if (typingEl) typingEl.remove();
            appendBotMessage("⚠️ Could not reach GradBot right now. Please ensure you are logged in and try again.", "bot");
        }
    };
}

/**
 * Creates and appends a styled chat bubble to `#botMessages` and scrolls to the bottom.
 *
 * @param {string} text             - Message text content to display.
 * @param {'user'|'bot'} sender     - Whether the bubble is from the user or GradBot.
 */
function appendBotMessage(text, sender) {
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
        div.innerText = text;
    } else {
        // Left-aligned white card bubble for GradBot responses
        div.className = "message other-message align-self-start";
        div.style.background = "#ffffff";
        div.style.border = "1px solid var(--cc-border)";
        div.style.borderRadius = "14px";
        div.style.borderBottomLeftRadius = "3px";
        div.style.padding = "0.75rem 1rem";
        div.style.maxWidth = "90%";
        div.style.fontSize = "0.9rem";
        div.style.boxShadow = "var(--cc-shadow-sm)";
        div.innerHTML = `
            <div class="d-flex align-items-center gap-1 mb-1 text-primary fw-bold" style="font-size: 0.78rem;">
                <i class="fas fa-robot"></i> GradBot
            </div>
            <div>${text}</div>
        `;
    }

    messagesArea.appendChild(div);
    messagesArea.scrollTop = messagesArea.scrollHeight;
}

