// =========================================================
// 1. IMPORTS (Links all your modules)
// =========================================================
import { getCurrentUser } from './utils/api.js';
import { fetchJobs } from './modules/jobs.js';
import { fetchEvents } from './modules/events.js';
import { connectChat, fetchRecentChats } from './modules/chat.js';
import { fetchPosts } from './modules/wall.js'; //
import './modules/feedback.js';
// =========================================================
// 2. MAIN INITIALIZATION
// =========================================================
document.addEventListener("DOMContentLoaded", async () => {
    // A. Security Check
    const user = getCurrentUser();
    console.log("Logged in as:", user);

    if (!user.email) {
        window.location.href = "login.html";
        return;
    }

    // B. Set UI Elements
    const welcomeEl = document.getElementById("welcomeUser");
    if(welcomeEl) welcomeEl.innerText = user.name;

    // C. Setup Admin/Role Visibility
    setupRoleBasedVisibility(user.role);

    // D. Connect WebSocket
    connectChat();

    // E. Load Default Tab (Wall or Messages)
    await window.switchTab('wall'); // changed default to wall, you can set to 'messages'
});

function setupRoleBasedVisibility(role) {
    if (role && role.toUpperCase() === 'ADMIN') {
        const adminTab = document.getElementById("tab-admin");
        if (adminTab) adminTab.classList.remove("d-none");
    }
}

// =========================================================
// 3. TAB SWITCHING LOGIC (The Traffic Controller)
// =========================================================
window.switchTab = async function(tab) {
    console.log("Switching to tab:", tab);

    // 1. Update Navbar Active State
    document.querySelectorAll('.nav-link').forEach(btn => btn.classList.remove('active'));
    const activeBtn = document.getElementById(`tab-${tab}`);
    if(activeBtn) activeBtn.classList.add('active');

    // 2. Show Loading Spinner
    const container = document.getElementById("main-content");
    container.innerHTML = `<div class="text-center py-5"><div class="spinner-border text-primary"></div></div>`;

    try {
        // 3. Load HTML Component
        // ?v=... prevents browser caching of the HTML file
        const response = await fetch(`components/${tab}.html?v=${Date.now()}`);
        if (!response.ok) throw new Error(`Failed to load ${tab}.html`);

        const html = await response.text();
        container.innerHTML = html;

        // 4. Run the correct Module Logic
        switch (tab) {
            case 'wall':
                fetchPosts(); // From wall.js
                break;
            case 'jobs':
                fetchJobs(); // From jobs.js
                break;
            case 'events':
                fetchEvents(); // From events.js
                break;
            case 'messages':
                fetchRecentChats(); // From chat.js
                break;
            case 'directory':
                // Dynamic Import (Loads directory.js only when clicked)
                import('./modules/directory.js').then(m => {
                    if(window.runSearch) window.runSearch();
                });
                break;
            case 'admin':
                // Dynamic Import (Loads admin.js only when clicked)
                import('./modules/admin.js').then(m => m.loadAdminModule());
                break;
            case 'profile':
                import('./modules/profile.js').then(m => m.loadProfile());
                break;
        }
    } catch (e) {
        console.error(e);
        container.innerHTML = `<div class="alert alert-danger">Error loading module: ${e.message}</div>`;
    }
};
// =========================================================
// GLOBAL LOGOUT FUNCTION
// =========================================================
window.logout = function() {
    // 1. Clear Security Token
    localStorage.removeItem("jwt_token");

    // 2. Clear User Data
    localStorage.removeItem("campus_user");

    // 3. Redirect to Login Page
    alert("Logged out successfully.");
    window.location.href = "index.html"; // Change to 'login.html' if that's your file name
};