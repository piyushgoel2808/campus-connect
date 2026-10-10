/**
 * ============================================================================
 * FILE: src/main/resources/static/js/app.js
 * PURPOSE: Authentication controller for `login.html`.
 *
 * OVERVIEW:
 *   Handles the user sign-in form submission on `login.html`, sends credentials
 *   to the backend authentication endpoint, stores the returned JWT and user
 *   session metadata in `localStorage`, and redirects authenticated users to
 *   `dashboard.html`.
 *
 * API ENDPOINTS USED:
 *   - POST `/api/auth/login` : Validates `{ email, password }` and returns
 *                              `{ token, id, name, role }`.
 *
 * DOM ELEMENTS REFERENCED (in `login.html`):
 *   - `#loginForm` : The login `<form>` element
 *   - `#email`     : Email input field
 *   - `#password`  : Password input field
 *   - `#alertBox`  : Bootstrap alert container for success/error feedback
 * ============================================================================
 */

// Base endpoint for authentication requests
const API_URL = "/api/auth";

/**
 * Attach form submission listener once the login page DOM has finished loading.
 */
document.addEventListener("DOMContentLoaded", () => {
    const loginForm = document.getElementById("loginForm");

    // Check if user is already logged in (Optional auto-redirect hook)
    if (localStorage.getItem("jwt_token")) {
        // window.location.href = "dashboard.html";
    }

    if (loginForm) {
        loginForm.addEventListener("submit", handleLogin);
    }
});

/**
 * Handles the `#loginForm` submit event.
 * Submits user credentials to `POST /api/auth/login`, persists the JWT session
 * into `localStorage` on success, and navigates to `dashboard.html`.
 *
 * @param {SubmitEvent} e - The form submission event.
 */
async function handleLogin(e) {
    e.preventDefault();

    const emailInput = document.getElementById("email");
    const passwordInput = document.getElementById("password");
    const alertBox = document.getElementById("alertBox");
    const submitBtn = e.target.querySelector("button[type='submit']");

    // 1. Reset UI and prevent multiple clicks while request is in flight
    alertBox.classList.add("d-none");
    alertBox.innerText = "";
    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.innerText = "Signing in...";
    }

    try {
        // Send credentials to backend AuthController
        const response = await fetch(`${API_URL}/login`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                email: emailInput.value,
                password: passwordInput.value
            })
        });

        // Parse response body safely (handles both JSON and plain-text error responses)
        const text = await response.text();
        let data = {};
        try {
            data = JSON.parse(text);
        } catch (parseErr) {
            data = { message: text };
        }

        if (response.ok) {
            // 2. Save Session Data to LocalStorage for downstream modules (`api.js`, `chat.js`, etc.)
            localStorage.setItem("jwt_token", data.token);
            localStorage.setItem("user_name", data.name);
            localStorage.setItem("user_role", data.role);
            localStorage.setItem("user_email", emailInput.value);

            // Save user ID (Essential for Chat & Profile modules)
            if (data.id) {
                localStorage.setItem("user_id", data.id);
            }

            // 3. Show Success Message
            alertBox.className = "alert alert-success";
            alertBox.innerText = "✅ Login Successful! Redirecting...";
            alertBox.classList.remove("d-none");

            // 4. Redirect to Dashboard after 1 second
            setTimeout(() => {
                window.location.href = "dashboard.html";
            }, 1000);

        } else {
            // Server returned an error (e.g., 401 Unauthorized / bad credentials)
            throw new Error(data.message || "Invalid Email or Password");
        }

    } catch (error) {
        console.error("Login Error:", error);

        // Show error message to user inside `#alertBox`
        alertBox.className = "alert alert-danger";
        alertBox.innerText = `❌ ${error.message || "Server Error. Is the backend running?"}`;
        alertBox.classList.remove("d-none");

        // 5. Re-enable button so user can try again
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.innerText = "Login";
        }
    }
}