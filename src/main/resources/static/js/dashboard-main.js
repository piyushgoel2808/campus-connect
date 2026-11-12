/**
 * Main Single-Page Application (SPA) shell router and initializer for `dashboard.html`.
 */

import { getCurrentUser } from './utils/api.js?v=20261112_1';
import { escapeHtml } from './utils/sanitize.js?v=20261110_1';
import { fetchJobs } from './modules/jobs.js?v=20261110_1';
import { fetchEvents } from './modules/events.js?v=20261110_1';
import { connectChat, disconnectChat, fetchRecentChats } from './modules/chat.js?v=20261112_2';
import { initNotifications } from './modules/notifications.js?v=20261110_1';
import { fetchPosts } from './modules/wall.js?v=20261110_1';
import './modules/feedback.js?v=20261110_1';
import { initBot } from './modules/bot.js?v=20261110_2';

document.addEventListener("DOMContentLoaded", async () => {
    const user = getCurrentUser();
    if (!user.email) {
        window.location.href = "login.html";
        return;
    }

    const welcomeEl = document.getElementById("welcomeUser");
    if (welcomeEl) welcomeEl.innerText = user.name || "User";
    const avatarInitialEl = document.getElementById("userAvatarInitial");
    if (avatarInitialEl && user.name) {
        avatarInitialEl.innerText = user.name.charAt(0).toUpperCase();
    }

    if (!user.passwordChanged) {
        const banner = document.getElementById("defaultPasswordBanner");
        if (banner) banner.classList.remove("d-none");
    }

    setupRoleBasedVisibility(user.role);

    connectChat();
    initNotifications();
    initBot();

    await window.switchTab('wall');
});

function setupRoleBasedVisibility(role) {
    if (role && role.toUpperCase() === 'ADMIN') {
        const adminTab = document.getElementById("tab-admin");
        const mobileAdminTab = document.getElementById("mobile-tab-admin");
        if (adminTab) adminTab.classList.remove("d-none");
        if (mobileAdminTab) mobileAdminTab.classList.remove("d-none");
    }
}

window.switchTab = async function(tab) {
    // 1. Update Desktop and Mobile Navbar Active States
    document.querySelectorAll('.nav-link').forEach(btn => btn.classList.remove('active'));
    document.querySelectorAll(`.nav-link[data-tab="${tab}"]`).forEach(btn => btn.classList.add('active'));
    const activeBtn = document.getElementById(`tab-${tab}`);
    if (activeBtn) activeBtn.classList.add('active');

    const mainCol = document.getElementById("mainContentCol");
    const chatCol = document.getElementById("chatColumn");

    if (tab === 'chat') {
        if (mainCol) mainCol.classList.add("d-none", "d-lg-block");
        if (chatCol) chatCol.classList.add("mobile-chat-active");
        const chatArea = document.getElementById("chatArea");
        if (chatArea) chatArea.scrollTop = chatArea.scrollHeight;
        const msgInput = document.getElementById("messageInput");
        if (msgInput) msgInput.focus();
        return;
    }

    if (mainCol) mainCol.classList.remove("d-none", "d-lg-block");
    if (chatCol) chatCol.classList.remove("mobile-chat-active");

    // 2. Clean up any previously hoisted component modals & lingering backdrops
    document.querySelectorAll('.modal[data-cc-dynamic-modal="true"]').forEach(modalEl => {
        try {
            const instance = bootstrap.Modal.getInstance(modalEl);
            if (instance) instance.dispose();
        } catch (_) {}
        modalEl.remove();
    });
    document.querySelectorAll('.modal-backdrop').forEach(el => el.remove());
    document.body.classList.remove('modal-open');
    document.body.style.removeProperty('overflow');
    document.body.style.removeProperty('padding-right');

    // 3. Show Loading Spinner
    const container = document.getElementById("main-content");
    container.innerHTML = `<div class="text-center py-5"><div class="spinner-border text-primary"></div></div>`;

    try {
        // 4. Load HTML Component
        const response = await fetch(`components/${tab}.html?v=${Date.now()}`);
        if (!response.ok) throw new Error(`Failed to load ${tab}.html`);

        const html = await response.text();
        container.innerHTML = html;

        // 5. Hoist any component modals to document.body
        container.querySelectorAll('.modal').forEach(modalEl => {
            if (modalEl.id) {
                const existing = document.getElementById(modalEl.id);
                if (existing && existing !== modalEl) {
                    existing.remove();
                }
            }
            modalEl.setAttribute('data-cc-dynamic-modal', 'true');
            document.body.appendChild(modalEl);
        });

        // 6. Initialize Tab Module
        switch (tab) {
            case 'wall':
                fetchPosts();
                break;
            case 'jobs':
                fetchJobs();
                break;
            case 'events':
                fetchEvents();
                break;
            case 'messages':
                fetchRecentChats();
                break;
            case 'directory':
                import('./modules/directory.js?v=20261110_1').then(m => {
                    if (window.runSearch) window.runSearch();
                });
                break;
            case 'admin':
                import('./modules/admin.js?v=20261112_1').then(m => m.loadAdminModule());
                break;
            case 'profile':
                import('./modules/profile.js?v=20261110_1').then(m => m.loadProfile());
                break;
        }
    } catch (e) {
        console.error(e);
        container.innerHTML = `<div class="alert alert-danger">Error loading module: ${escapeHtml(e.message)}</div>`;
    }
};

window.logout = function() {
    try {
        disconnectChat();
    } catch (_) {}

    localStorage.removeItem("jwt_token");
    localStorage.removeItem("user_name");
    localStorage.removeItem("user_role");
    localStorage.removeItem("user_email");
    localStorage.removeItem("user_id");
    localStorage.removeItem("campus_user");
    localStorage.removeItem("password_changed");

    window.location.href = "login.html";
};