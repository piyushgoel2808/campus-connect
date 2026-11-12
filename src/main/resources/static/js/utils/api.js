/**
 * Centralized HTTP client utility and session state helper for GradLink.
 */

const API_BASE = "/api";
let isRedirectingToLogin = false;

function getToken() {
    return localStorage.getItem("jwt_token");
}

function clearSessionStorage() {
    localStorage.removeItem("jwt_token");
    localStorage.removeItem("user_name");
    localStorage.removeItem("user_role");
    localStorage.removeItem("user_email");
    localStorage.removeItem("user_id");
    localStorage.removeItem("campus_user");
    localStorage.removeItem("password_changed");
}

function redirectToLogin(message) {
    clearSessionStorage();
    if (!isRedirectingToLogin) {
        isRedirectingToLogin = true;
        if (message) {
            alert(message);
        }
        window.location.href = "login.html";
    }
}

export function handleUnauthorized(message = "Session expired. Please sign in again.") {
    redirectToLogin(message);
}

/**
 * Extracts a human-readable error message from a failed Fetch `Response`.
 */
export async function parseErrorMessage(response, fallback = "Request failed") {
    const status = response?.status;
    const statusFallback = status ? `${fallback} (HTTP ${status})` : fallback;
    try {
        const text = await response.text();
        if (!text) return statusFallback;
        try {
            const json = JSON.parse(text);
            return json.error || json.message || statusFallback;
        } catch (_) {
            const trimmed = text.trim();
            if (!trimmed || trimmed.startsWith("<")) {
                return statusFallback;
            }
            return trimmed.length > 260 ? `${trimmed.slice(0, 260)}...` : trimmed;
        }
    } catch (_) {
        return statusFallback;
    }
}

/**
 * Performs an authenticated HTTP GET request and returns parsed JSON.
 */
export async function get(endpoint) {
    const token = getToken();
    if (!token) {
        redirectToLogin();
        throw new Error("Authentication required");
    }

    const response = await fetch(`${API_BASE}${endpoint}`, {
        headers: { "Authorization": `Bearer ${token}` }
    });

    if (response.status === 401) {
        redirectToLogin("Session expired. Please sign in again.");
        throw new Error("Session expired");
    }

    if (!response.ok) {
        const message = await parseErrorMessage(response, `Request failed (${response.status})`);
        throw new Error(message);
    }

    const rawBody = await response.text();
    if (!rawBody) return null;
    try {
        return JSON.parse(rawBody);
    } catch (_) {
        return rawBody;
    }
}

/**
 * Performs an authenticated HTTP POST / PUT / DELETE request and returns the raw Response.
 */
export async function send(endpoint, method, data = null) {
    const token = getToken();
    if (!token) {
        redirectToLogin();
        throw new Error("Authentication required");
    }

    const options = {
        method,
        headers: {
            "Authorization": `Bearer ${token}`,
            "Content-Type": "application/json"
        }
    };

    if (data !== null && data !== undefined) {
        options.body = JSON.stringify(data);
    }

    const response = await fetch(`${API_BASE}${endpoint}`, options);

    if (response.status === 401) {
        redirectToLogin("Session expired. Please sign in again.");
        throw new Error("Session expired");
    }

    return response;
}

/**
 * Reads the currently authenticated user's session summary from localStorage.
 */
export function getCurrentUser() {
    return {
        email: localStorage.getItem("user_email"),
        role: localStorage.getItem("user_role"),
        name: localStorage.getItem("user_name"),
        passwordChanged: localStorage.getItem("password_changed") !== "false"
    };
}