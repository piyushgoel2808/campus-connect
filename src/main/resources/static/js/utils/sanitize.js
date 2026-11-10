/**
 * Shared frontend sanitization, URL validation, and date formatting helpers.
 */

/**
 * Escapes HTML special characters so untrusted strings cannot execute markup/scripts in templates.
 *
 * @param {any} value - Raw text value.
 * @returns {string} HTML-escaped string.
 */
export function escapeHtml(value) {
    if (value === null || value === undefined) return "";
    return String(value)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

/**
 * Validates and normalizes an external HTTP/HTTPS URL, rejecting dangerous schemes
 * such as `javascript:`, `data:`, `vbscript:`, or protocol-relative `//` URLs.
 *
 * @param {string} rawUrl - User-supplied URL string.
 * @returns {string} Safe normalized URL or empty string if invalid/unsafe.
 */
export function safeHttpUrl(rawUrl) {
    if (!rawUrl) return "";
    const trimmed = String(rawUrl).trim();
    if (!trimmed || trimmed.startsWith("//") || /[\u0000-\u001F\u007F]/.test(trimmed)) {
        return "";
    }

    const hasExplicitScheme = /^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(trimmed);
    const candidate = hasExplicitScheme ? trimmed : `https://${trimmed}`;

    try {
        const parsed = new URL(candidate);
        if (parsed.protocol === "http:" || parsed.protocol === "https:") {
            return parsed.href;
        }
    } catch (_) {
        return "";
    }
    return "";
}

/**
 * Validates a media URL for post images, allowing relative `/uploads/...` paths
 * returned by the backend upload endpoint or safe `http(s):` URLs.
 *
 * @param {string} rawUrl - Image URL or uploaded relative path.
 * @returns {string} Safe URL string or empty string if rejected.
 */
export function safeMediaUrl(rawUrl) {
    if (!rawUrl) return "";
    const trimmed = String(rawUrl).trim();
    if (!trimmed || /[\u0000-\u001F\u007F]/.test(trimmed)) return "";
    if (trimmed.startsWith("/uploads/") && !trimmed.startsWith("//") && !trimmed.includes("..")) {
        return trimmed;
    }
    return safeHttpUrl(trimmed);
}

/**
 * Formats an ISO-8601 timestamp into a concise relative age string.
 *
 * @param {string|number|Date} dateInput - Timestamp value.
 * @returns {string} Relative time label (e.g., "Just now", "5m ago", "2h ago").
 */
export function formatTimeAgo(dateInput) {
    if (!dateInput) return "";
    const date = new Date(dateInput);
    const ms = date.getTime();
    if (Number.isNaN(ms)) return "";

    const seconds = Math.max(0, Math.floor((Date.now() - ms) / 1000));
    const intervals = [
        [31536000, "y"],
        [2592000, "mo"],
        [86400, "d"],
        [3600, "h"],
        [60, "m"]
    ];
    for (const [unitSeconds, suffix] of intervals) {
        const count = Math.floor(seconds / unitSeconds);
        if (count >= 1) return `${count}${suffix} ago`;
    }
    return seconds < 10 ? "Just now" : `${seconds}s ago`;
}
