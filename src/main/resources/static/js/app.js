// Configuration
const API_URL = "/api/auth";

document.addEventListener("DOMContentLoaded", () => {
    const loginForm = document.getElementById("loginForm");

    // Check if user is already logged in (Optional)
    if (localStorage.getItem("jwt_token")) {
        // window.location.href = "dashboard.html";
    }

    if (loginForm) {
        loginForm.addEventListener("submit", handleLogin);
    }
});

async function handleLogin(e) {
    e.preventDefault();

    const emailInput = document.getElementById("email");
    const passwordInput = document.getElementById("password");
    const alertBox = document.getElementById("alertBox");
    const submitBtn = e.target.querySelector("button[type='submit']");

    // 1. Reset UI and prevent multiple clicks
    alertBox.classList.add("d-none");
    alertBox.innerText = "";
    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.innerText = "Signing in...";
    }

    try {
        const response = await fetch(`${API_URL}/login`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                email: emailInput.value,
                password: passwordInput.value
            })
        });

        const data = await response.json();

        if (response.ok) {
            // 2. Save Session Data to LocalStorage
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
            // Server returned an error (e.g., 401 Unauthorized)
            throw new Error(data.message || "Invalid Email or Password");
        }

    } catch (error) {
        console.error("Login Error:", error);

        // Show error message to user
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