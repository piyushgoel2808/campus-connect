const API_URL = "http://localhost:8080/api/auth";

document.getElementById("loginForm").addEventListener("submit", async function(e) {
    e.preventDefault(); // Stop page reload

    const email = document.getElementById("email").value;
    const password = document.getElementById("password").value;
    const alertBox = document.getElementById("alertBox");

    try {
        const response = await fetch(`${API_URL}/login`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email: email, password: password })
        });

        if (response.ok) {
            const data = await response.json();
            
            // Save the Token securely in the browser
            localStorage.setItem("jwt_token", data.token);
            localStorage.setItem("user_name", data.name);
            localStorage.setItem("user_role", data.role);

            // Success Visuals
            alertBox.className = "alert alert-success";
            alertBox.innerText = `✅ Welcome back, ${data.name}!`;
            alertBox.classList.remove("d-none");

            // Redirect logic would go here (e.g., window.location.href = "dashboard.html")
            console.log("Token:", data.token);
            setTimeout(() => { window.location.href = "dashboard.html"; }, 1000);

        } else {
            alertBox.className = "alert alert-danger";
            alertBox.innerText = "❌ Invalid Credentials";
            alertBox.classList.remove("d-none");
        }

    } catch (error) {
        console.error("Error:", error);
        alertBox.innerText = "⚠️ Server Error. Is Spring Boot running?";
        alertBox.classList.remove("d-none");
    }
});
