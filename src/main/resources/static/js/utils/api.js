/**
 * ============================================================================
 * FILE: src/main/resources/static/js/utils/api.js
 * PURPOSE: Centralized HTTP client utility and session state helper for the
 *          GradLink (Campus Connect) frontend.
 *
 * OVERVIEW:
 *   All frontend feature modules import `get`, `send`, and `getCurrentUser`
 *   from this utility rather than calling `fetch()` directly. It automatically:
 *     - Prefixes all relative API paths with `/api`.
 *     - Attaches the JWT Bearer token from `localStorage` (`jwt_token`).
 *     - Redirects unauthenticated or expired sessions (HTTP 401) to `login.html`.
 *
 * STORAGE KEYS USED (localStorage):
 *   - `jwt_token`  : JWT authentication token issued by `/api/auth/login`
 *   - `user_email` : Logged-in user's email address
 *   - `user_role`  : Logged-in user's role (`STUDENT`, `ALUMNI`, `FACULTY`, `ADMIN`)
 *   - `user_name`  : Logged-in user's display name
 * ============================================================================
 */

// Base URL prefix for all backend REST API endpoints
const API_BASE = "/api";

/**
 * Retrieves the active JWT token from browser localStorage.
 *
 * @returns {string|null} The stored JWT string, or null if not logged in.
 */
function getToken() {
    return localStorage.getItem("jwt_token");
}

/**
 * 1. Generic GET Request
 * Performs an authenticated HTTP GET request to `${API_BASE}${endpoint}` and
 * parses the JSON response payload. Redirects to `login.html` if no token is
 * present or if the backend returns HTTP 401 Unauthorized.
 *
 * @param {string} endpoint - Relative API path starting with `/` (e.g., `/jobs`, `/users`).
 * @returns {Promise<any>} Parsed JSON response body from the server.
 */
export async function get(endpoint) {
    const token = getToken();
    // Guard: redirect immediately if the user does not have a saved JWT token
    if (!token) window.location.href = "login.html";

    // Execute GET request with Bearer authentication header
    const response = await fetch(`${API_BASE}${endpoint}`, {
        headers: { "Authorization": `Bearer ${token}` }
    });

    // Handle expired or invalid JWT token
    if (response.status === 401) {
        alert("Session expired");
        window.location.href = "login.html";
    }
    return response.json();
}

/**
 * 2. Generic POST / PUT / DELETE Request
 * Performs an authenticated HTTP mutation request with an optional JSON body.
 * Returns the raw `Response` object so callers can inspect `response.ok`,
 * `response.status`, or read text/JSON responses as needed.
 *
 * @param {string} endpoint - Relative API path starting with `/` (e.g., `/posts`, `/jobs/5`).
 * @param {string} method   - HTTP verb (`POST`, `PUT`, `DELETE`).
 * @param {Object|null} [data=null] - Optional payload object to serialize as JSON.
 * @returns {Promise<Response>} Raw Fetch API Response object.
 */
export async function send(endpoint, method, data = null) {
    const token = getToken();
    const options = {
        method: method,
        headers: {
            "Authorization": `Bearer ${token}`,
            "Content-Type": "application/json"
        }
    };

    // Attach serialized JSON request body when payload data is provided
    if (data) options.body = JSON.stringify(data);

    const response = await fetch(`${API_BASE}${endpoint}`, options);
    return response; // Return full response to check .ok
}

/**
 * 3. User State Helper
 * Reads the currently authenticated user's profile summary from localStorage.
 * Populated during login in `app.js`.
 *
 * @returns {{ email: string|null, role: string|null, name: string|null }}
 */
export function getCurrentUser() {
    return {
        email: localStorage.getItem("user_email"),
        role: localStorage.getItem("user_role"),
        name: localStorage.getItem("user_name")
    };
}