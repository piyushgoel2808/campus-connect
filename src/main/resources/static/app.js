const API_URL = "/api/auth"; // Relative path

document.getElementById("loginForm").addEventListener("submit", async function(e) {
    e.preventDefault(); // Stop page reload

    const email = document.getElementById("email").value;
    const password = document.getElementById("password").value;
    const alertBox = document.getElementById("alertBox");

    // Clear previous alerts
    alertBox.classList.add("d-none");
    alertBox.innerText = "";

    try {
        const response = await fetch(`${API_URL}/login`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email: email, password: password })
        });

        if (response.ok) {
            const data = await response.json();
            
            // 1. Save Token
            localStorage.setItem("jwt_token", data.token);
            localStorage.setItem("user_name", data.name);
            localStorage.setItem("user_role", data.role);
            localStorage.setItem("user_email", email); // Important for Chat!

            // 2. Show Success
            alertBox.className = "alert alert-success";
            alertBox.innerText = `✅ Login Successful! Redirecting...`;
            alertBox.classList.remove("d-none");

            // 3. Redirect to Dashboard
            setTimeout(() => {
                window.location.href = "dashboard.html"; 
            }, 1000);

        } else {
            alertBox.className = "alert alert-danger";
            alertBox.innerText = "❌ Invalid Email or Password";
            alertBox.classList.remove("d-none");
        }

    } catch (error) {
        console.error("Error:", error);
        alertBox.className = "alert alert-danger";
        alertBox.innerText = "⚠️ Server Error. Is the backend running?";
        alertBox.classList.remove("d-none");
    }
});