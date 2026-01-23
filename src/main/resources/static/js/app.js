// Configuration
const API_AUTH_URL = "/api/auth";

document.addEventListener("DOMContentLoaded", () => {

    // 1. Check if already logged in
    if (localStorage.getItem("jwt_token")) {
        // Optional: Auto-redirect if token exists
        // window.location.href = "dashboard.html";
    }

    // 2. Attach Login Listener
    const loginForm = document.getElementById("loginForm");
    if (loginForm) {
        loginForm.addEventListener("submit", handleLogin);
    }
});

async function handleLogin(e) {
    e.preventDefault();

    const emailInput = document.getElementById("email");
    const passwordInput = document.getElementById("password");
    const alertBox = document.getElementById("alertBox");
    const submitBtn = document.querySelector("button[type='submit']");

    // Reset UI
    alertBox.classList.add("d-none");
    alertBox.innerText = "";
    submitBtn.disabled = true;
    submitBtn.innerText = "Signing in...";

    const payload = {
        email: emailInput.value,
        password: passwordInput.value
    };

    try {
        const response = await fetch(`${API_AUTH_URL}/login`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        const data = await response.json();

        if (response.ok) {
            // ✅ SUCCESS: Save Session Data
            localStorage.setItem("jwt_token", data.token);
            localStorage.setItem("user_email", data.email || payload.email);
            localStorage.setItem("user_name", data.name);
            localStorage.setItem("user_role", data.role);

            // IMPORTANT: Save ID for Chat/Profile modules
            if (data.id) {
                localStorage.setItem("user_id", data.id);
            }

            // Show success message
            alertBox.className = "alert alert-success";
            alertBox.innerText = "✅ Login Successful! Redirecting...";
            alertBox.classList.remove("d-none");

            // Redirect
            setTimeout(() => {
                window.location.href = "dashboard.html";
            }, 1000);

        } else {
            // ❌ FAIL: Show Error
            throw new Error(data.message || "Invalid credentials");
        }

    } catch (error) {
        console.error("Login Error:", error);
        alertBox.className = "alert alert-danger";
        alertBox.innerText = `❌ ${error.message || "Server Error"}`;
        alertBox.classList.remove("d-none");

        // Reset Button
        submitBtn.disabled = false;
        submitBtn.innerText = "Login";
    }
}