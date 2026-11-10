/**
 * Authentication controller for `login.html` (Sign In + Student/Alumni Self-Registration).
 */

const API_URL = "/api/auth";
const DEFAULT_LOGIN_BTN_HTML = '<span>Sign In to GradLink</span><i class="fas fa-arrow-right ms-2"></i>';
const DEFAULT_REG_BTN_HTML = '<span>Create GradLink Account</span><i class="fas fa-user-plus ms-2"></i>';

document.addEventListener("DOMContentLoaded", () => {
    const loginForm = document.getElementById("loginForm");
    const registerForm = document.getElementById("registerForm");

    if (loginForm) {
        loginForm.addEventListener("submit", handleLogin);
    }
    if (registerForm) {
        registerForm.addEventListener("submit", handleRegister);
    }
});

window.switchAuthMode = function(mode) {
    const loginForm = document.getElementById("loginForm");
    const registerForm = document.getElementById("registerForm");
    const loginTab = document.getElementById("showLoginTab");
    const regTab = document.getElementById("showRegisterTab");
    const subtitle = document.getElementById("authSubtitle");
    const alertBox = document.getElementById("alertBox");

    if (alertBox) {
        alertBox.classList.add("d-none");
        alertBox.innerText = "";
    }

    if (mode === "register") {
        loginForm?.classList.add("d-none");
        registerForm?.classList.remove("d-none");
        loginTab?.classList.replace("btn-primary", "btn-outline-primary");
        regTab?.classList.replace("btn-outline-primary", "btn-primary");
        if (subtitle) subtitle.innerText = "Create your BVICAM student or alumni account to join GradLink.";
    } else {
        registerForm?.classList.add("d-none");
        loginForm?.classList.remove("d-none");
        regTab?.classList.replace("btn-primary", "btn-outline-primary");
        loginTab?.classList.replace("btn-outline-primary", "btn-primary");
        if (subtitle) subtitle.innerText = "Sign in to connect with students, alumni, jobs, events, and the GradLink community.";
    }
};

function showAlert(message, isSuccess = false) {
    const alertBox = document.getElementById("alertBox");
    if (!alertBox) return;
    alertBox.className = isSuccess ? "alert alert-success" : "alert alert-danger";
    alertBox.innerText = message;
    alertBox.classList.remove("d-none");
}

async function parseAuthPayload(response) {
    const text = await response.text();
    if (!text) return {};
    try {
        return JSON.parse(text);
    } catch (_) {
        return { message: text };
    }
}

async function handleLogin(e) {
    e.preventDefault();

    const emailInput = document.getElementById("email");
    const passwordInput = document.getElementById("password");
    const alertBox = document.getElementById("alertBox");
    const submitBtn = document.getElementById("loginSubmitBtn") || e.target.querySelector("button[type='submit']");

    if (alertBox) {
        alertBox.classList.add("d-none");
        alertBox.innerText = "";
    }
    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.innerText = "Signing in...";
    }

    try {
        const email = emailInput.value.trim().toLowerCase();
        const response = await fetch(`${API_URL}/login`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                email,
                password: passwordInput.value
            })
        });

        const data = await parseAuthPayload(response);

        if (response.ok) {
            localStorage.setItem("jwt_token", data.token);
            localStorage.setItem("user_name", data.name);
            localStorage.setItem("user_role", data.role);
            localStorage.setItem("user_email", email);
            localStorage.setItem("password_changed", String(data.passwordChanged !== false));
            if (data.id) {
                localStorage.setItem("user_id", data.id);
            }

            showAlert("✅ Login Successful! Redirecting...", true);
            setTimeout(() => {
                window.location.href = "dashboard.html";
            }, 600);
        } else {
            throw new Error(data.error || data.message || "Invalid Email or Password");
        }
    } catch (error) {
        console.error("Login Error:", error);
        showAlert(`❌ ${error.message || "Server Error. Is the backend running?"}`);

        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.innerHTML = DEFAULT_LOGIN_BTN_HTML;
        }
    }
}

async function handleRegister(e) {
    e.preventDefault();

    const name = document.getElementById("regName")?.value.trim() || "";
    const email = document.getElementById("regEmail")?.value.trim().toLowerCase() || "";
    const role = document.getElementById("regRole")?.value || "STUDENT";
    const departmentCode = document.getElementById("regDept")?.value || "";
    const batchRaw = document.getElementById("regBatch")?.value.trim() || "";
    const enrollmentNumber = document.getElementById("regEnrollment")?.value.trim() || "";
    const password = document.getElementById("regPassword")?.value || "";
    const submitBtn = document.getElementById("registerSubmitBtn");

    if (password.length < 6) {
        showAlert("❌ Password must be at least 6 characters long.");
        return;
    }

    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.innerText = "Creating account...";
    }

    try {
        const payload = {
            name,
            email,
            password,
            role,
            departmentCode: departmentCode || null,
            batchYear: batchRaw ? parseInt(batchRaw, 10) : null,
            enrollmentNumber: enrollmentNumber || null
        };

        const response = await fetch(`${API_URL}/register`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        const data = await parseAuthPayload(response);
        if (!response.ok) {
            throw new Error(data.error || data.message || "Registration failed.");
        }

        window.switchAuthMode("login");
        const loginEmailEl = document.getElementById("email");
        if (loginEmailEl) loginEmailEl.value = email;
        showAlert("✅ Account created! Sign in with your new credentials.", true);
        e.target.reset();
    } catch (error) {
        console.error("Registration Error:", error);
        showAlert(`❌ ${error.message || "Unable to register."}`);
    } finally {
        if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.innerHTML = DEFAULT_REG_BTN_HTML;
        }
    }
}
