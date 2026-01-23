const API_BASE = "/api";

// Helper to get token
function getToken() {
    return localStorage.getItem("jwt_token");
}

// 1. Generic GET Request
export async function get(endpoint) {
    const token = getToken();
    if (!token) window.location.href = "login.html";

    const response = await fetch(`${API_BASE}${endpoint}`, {
        headers: { "Authorization": `Bearer ${token}` }
    });

    if (response.status === 401) {
        alert("Session expired");
        window.location.href = "login.html";
    }
    return response.json();
}

// 2. Generic POST/PUT/DELETE Request
export async function send(endpoint, method, data = null) {
    const token = getToken();
    const options = {
        method: method,
        headers: {
            "Authorization": `Bearer ${token}`,
            "Content-Type": "application/json"
        }
    };

    if (data) options.body = JSON.stringify(data);

    const response = await fetch(`${API_BASE}${endpoint}`, options);
    return response; // Return full response to check .ok
}

// 3. User State Helper
export function getCurrentUser() {
    return {
        email: localStorage.getItem("user_email"),
        role: localStorage.getItem("user_role"),
        name: localStorage.getItem("user_name")
    };
}