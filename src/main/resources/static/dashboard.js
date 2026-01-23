/**
 * ====================================================================
 * DASHBOARD.JS
 * Main Application Logic for the Alumni Portal.
 * * RESPONSIBILITIES:
 * 1. Authentication Check & User Session Management
 * 2. Real-time WebSocket Chat (Public & Private)
 * 3. Dynamic Module Loading (Events, Jobs, Directory, etc.)
 * 4. CRUD Operations for all modules via REST API
 * ====================================================================
 */

// =========================================================
// 1. GLOBAL CONFIGURATION & STATE
// =========================================================

// API Endpoints
const API_URL = "/api";  // Base URL for REST calls (Spring Boot Controller)
const WS_URL = "/ws";    // Base URL for WebSocket connection

// User Session State (Retrieved from Login)
const token = localStorage.getItem("jwt_token");
let currentUserEmail = localStorage.getItem("user_email");
let currentUserName = localStorage.getItem("user_name");
let currentUserRole = localStorage.getItem("user_role"); // 'STUDENT', 'ALUMNI', 'ADMIN'
let currentUserId = null;

// Chat System State
let stompClient = null;           // The WebSocket client object
let currentChatPartnerEmail = null; // Email of the user we are currently privately messaging

// UI State Helpers
let searchTimeout = null;         // For debouncing search inputs
let selectedUserForModal = null;  // Temporary storage for the user currently being viewed

// =========================================================
// 2. INITIALIZATION (On Page Load)
// =========================================================
document.addEventListener("DOMContentLoaded", async () => {
    try {
        // A. Security Check: If no token, kick to login
        if (!token) {
            console.warn("⛔ No session token found. Redirecting to login.");
            window.location.href = "login.html";
            return;
        }

        // B. Set Welcome Message
        const welcomeEl = document.getElementById("welcomeUser");
        if (welcomeEl) welcomeEl.innerText = currentUserName || "User";

        // C. Setup Admin Features (Show/Hide tabs based on role)
        setupRoleBasedVisibility();

        // D. Fetch latest user data (sync with DB)
        await ensureUserData();

        // E. Connect to Real-time Chat
        connectChat();

        // F. Load the default tab (Messages or Wall)
        await switchTab('messages');

    } catch (error) {
        console.error("🔥 Critical Startup Error:", error);
        alert("Session expired or invalid. Please login again.");
        logout();
    } finally {
        hideSpinner();
    }
});

// =========================================================
// 3. HELPER FUNCTIONS (Navigation, Auth, UI)
// =========================================================

/** Hides the global loading spinner */
function hideSpinner() {
    const ids = ["loading", "spinner", "loader", "loading-overlay"];
    ids.forEach(id => {
        const el = document.getElementById(id);
        if (el) el.style.display = "none";
    });
}

/** Shows/Hides UI elements based on User Role (Admin/Alumni/Student) */
function setupRoleBasedVisibility() {
    // Show Admin Tab
    if (currentUserRole === 'ADMIN') {
        const adminTab = document.getElementById("tab-admin");
        if (adminTab) adminTab.classList.remove("d-none");
    }
    // Show "Post Job" button (Alumni & Admin only)
    if (currentUserRole === 'ADMIN' || currentUserRole === 'ALUMNI') {
        const btn = document.getElementById("btnPostJob");
        if(btn) btn.classList.remove("d-none");
    }
}

/** Fetches fresh user data from API to ensure we have the correct ID/Email */
async function ensureUserData() {
    if (!currentUserEmail) {
        try {
            const res = await fetch(`${API_URL}/users/me`, {
                headers: { "Authorization": `Bearer ${token}` }
            });
            if (res.ok) {
                const user = await res.json();
                // Update Globals
                currentUserEmail = user.email;
                currentUserName = user.name;
                currentUserRole = user.role;
                currentUserId = user.id;

                // Update Storage
                localStorage.setItem("user_email", user.email);
                localStorage.setItem("user_name", user.name);
                localStorage.setItem("user_role", user.role);
            }
        } catch (e) {
            console.warn("Could not sync user details:", e);
        }
    }
}

/** Clears session and redirects to Login */
function logout() {
    if(stompClient) stompClient.disconnect();
    localStorage.clear();
    window.location.href = "login.html";
}

// =========================================================
// 4. DYNAMIC TAB NAVIGATION system
// =========================================================

/**
 * Loads HTML components dynamically into the main content area.
 * This makes the app feel like a Single Page Application (SPA).
 */
async function switchTab(tab) {
    // 1. Update Active Navbar Link
    document.querySelectorAll('.nav-link').forEach(btn => btn.classList.remove('active'));
    const btn = document.getElementById(`tab-${tab}`);
    if (btn) btn.classList.add('active');

    // 2. Show Local Spinner
    const container = document.getElementById("main-content");
    container.innerHTML = `<div class="text-center py-5"><div class="spinner-border text-primary"></div></div>`;

    try {
        // 3. Fetch HTML Component
        // Note: ?v=timestamp prevents browser caching of HTML files
        const response = await fetch(`components/${tab}.html?v=${Date.now()}`);
        if (!response.ok) throw new Error(`Component '${tab}' not found`);

        const html = await response.text();
        container.innerHTML = html;

        // 4. Initialize Logic for the specific module
        switch (tab) {
            case 'messages':
                fetchRecentChats();
                break;
            case 'directory':
                runSearch();
                break;
            case 'wall':
                fetchPosts();
                break;
            case 'profile':
                loadProfile();
                break;
            case 'events':
                fetchEvents();
                // Delayed check for Admin button in events (wait for HTML render)
                if (currentUserRole === "ADMIN") {
                    setTimeout(() => document.getElementById("btnAddEvent")?.classList.remove("d-none"), 100);
                }
                break;
            case 'jobs':
                fetchJobs();
                break;
            case 'admin':
                loadAdminModule();
                break;
        }

    } catch (e) {
        container.innerHTML = `<div class="alert alert-danger m-3">Error loading module: ${e.message}</div>`;
        console.error(e);
    }
}

// =========================================================
// 5. CHAT SYSTEM (WebSocket + Stomp + REST Hybrid)
// =========================================================

/** * 1. CONNECT
 * Establishes the WebSocket connection via SockJS and Stomp.
 */
function connectChat() {
    if (stompClient && stompClient.connected) return;

    var socket = new SockJS(WS_URL);
    stompClient = Stomp.over(socket);
    stompClient.debug = null; // Set to console.log for debugging, null for production

    stompClient.connect({}, function (frame) {
        console.log('✅ WebSocket Connected');

        // A. Subscribe to PUBLIC Chat
        stompClient.subscribe('/topic/public', function (payload) {
            onPublicMessageReceived(JSON.parse(payload.body));
        });

        // B. Subscribe to PRIVATE Chat (Real-time)
        // This listens to messages sent specifically to this user
        stompClient.subscribe('/user/queue/messages', function (payload) {
            onPrivateMessageReceived(JSON.parse(payload.body));
        });

    }, function (error) {
        console.error('❌ WebSocket Error:', error);
        // Retry connection after 5 seconds if failed
        setTimeout(connectChat, 5000);
    });
}

/** * 2. SEND PUBLIC MESSAGE
 * Sends a message to the global chat room.
 */
function sendMessage() {
    var input = document.getElementById("messageInput");
    var content = input.value.trim();

    if (content && stompClient) {
        var chatMessage = {
            senderName: currentUserEmail,
            content: content,
            type: 'CHAT'
        };
        // Destination matches @MessageMapping("/chat.sendMessage") in Controller
        stompClient.send("/app/chat.sendMessage", {}, JSON.stringify(chatMessage));
        input.value = '';
    }
}

/** * 3. START PRIVATE CHAT (Triggered by UI Button)
 * Opens the modal, sets the partner, and loads history.
 */
async function startDirectChat(userId, userName, userEmail) {
    currentChatPartnerEmail = userEmail; // Crucial: Sets who we are talking to

    // Update Modal Title
    const title = document.getElementById("dmModalTitle");
    if(title) title.innerText = "Chat with " + userName;

    // Show Modal
    const modalEl = document.getElementById('dmModal');
    if(modalEl) new bootstrap.Modal(modalEl).show();

    // Load Previous History via REST
    await loadChatHistory(userEmail);
}

/** * 4. LOAD HISTORY (REST API)
 * Fetches past messages from the database.
 */
async function loadChatHistory(partnerEmail) {
    const container = document.getElementById("dmMessagesArea");
    if(!container) return;
    container.innerHTML = "<div class='text-center small text-muted mt-2'>Loading history...</div>";

    try {
        const res = await fetch(`${API_URL}/messages/history?partnerEmail=${partnerEmail}`, {
            headers: { "Authorization": `Bearer ${token}` }
        });

        if (res.ok) {
            const messages = await res.json();
            container.innerHTML = "";
            messages.forEach(msg => {
                // Determine alignment
                const who = (msg.senderName === currentUserEmail || msg.senderEmail === currentUserEmail) ? 'me' : 'them';
                appendDirectMessage(msg.content, who);
            });
            // Scroll to bottom
            container.scrollTop = container.scrollHeight;
        }
    } catch (e) {
        container.innerHTML = "<div class='text-danger text-center'>Failed to load history.</div>";
    }
}

/** * 5. SEND PRIVATE MESSAGE (WebSocket)
 * Sends a real-time message to the specific user.
 */
function sendDirectMessage() {
    var input = document.getElementById("dmInput");
    var content = input.value.trim();

    if (content && currentChatPartnerEmail && stompClient) {
        var chatMessage = {
            senderName: currentUserEmail,
            receiverName: currentChatPartnerEmail,
            content: content,
            type: 'CHAT'
        };

        // Destination matches @MessageMapping("/chat.private") in Controller
        stompClient.send("/app/chat.private", {}, JSON.stringify(chatMessage));

        input.value = '';

        // Optimistic UI Update: Show my message immediately
        appendDirectMessage(content, 'me');
    }
}

/** 6. RECEIVE PUBLIC MESSAGE */
function onPublicMessageReceived(message) {
    var messageArea = document.getElementById("chatMessages");
    if (!messageArea) return;

    var li = document.createElement('li');
    li.className = 'list-group-item';
    li.innerHTML = `<strong>${message.senderName || 'User'}</strong>: ${message.content}`;

    messageArea.appendChild(li);
    messageArea.scrollTop = messageArea.scrollHeight;
}

/** 7. RECEIVE PRIVATE MESSAGE */
function onPrivateMessageReceived(message) {
    // Logic: Only display if the modal for THIS specific person is open
    if (currentChatPartnerEmail &&
       (message.senderName === currentChatPartnerEmail || message.receiverName === currentChatPartnerEmail)) {

        const who = (message.senderName === currentUserEmail) ? 'me' : 'them';

        // Prevent duplicates if I handled my own message optimistically
        if(who === 'them') {
             appendDirectMessage(message.content, who);
        }
    } else {
        console.log("📩 New background message from " + message.senderName);
        // Refresh "Recent Chats" list if user is on Messages tab
        if(document.getElementById("recentChatsList")) fetchRecentChats();
    }
}

/** 8. UI HELPER: Append Bubble */
function appendDirectMessage(text, who) {
    const container = document.getElementById("dmMessagesArea");
    if (!container) return;

    const div = document.createElement("div");
    // Styling for Me (Right/Blue) vs Them (Left/Grey)
    div.className = who === 'me'
        ? "alert alert-primary text-end mb-1 ms-auto"
        : "alert alert-secondary text-start mb-1 me-auto";
    div.style.maxWidth = "75%";
    div.innerText = text;

    container.appendChild(div);
    container.scrollTop = container.scrollHeight;
}

/** 9. FETCH RECENT CONVERSATIONS (Sidebar List) */
async function fetchRecentChats() {
    try {
        const res = await fetch(`${API_URL}/messages/partners`, { headers: { "Authorization": `Bearer ${token}` } });
        if(res.ok) {
            const users = await res.json();
            const list = document.getElementById("recentChatsList");
            if(!list) return;

            list.innerHTML = "";
            if(users.length === 0) list.innerHTML = `<div class="text-center py-5 text-muted">No chats yet.</div>`;

            users.forEach(u => {
                const initial = u.name ? u.name.charAt(0).toUpperCase() : '?';
                list.innerHTML += `
                    <div class="chat-item d-flex align-items-center p-3 border-bottom list-group-item-action"
                         style="cursor:pointer"
                         onclick="startDirectChat('${u.id}', '${u.name}', '${u.email}')">
                        <div class="avatar-circle me-3 bg-secondary text-white rounded-circle d-flex align-items-center justify-content-center" style="width:40px;height:40px">${initial}</div>
                        <div>
                            <div class="fw-bold text-dark">${u.name}</div>
                            <small class="text-muted">${u.headline||u.role}</small>
                        </div>
                    </div>`;
            });
        }
    } catch (e) { console.error(e); }
}

// =========================================================
// 6. MODULE: DIRECTORY & SEARCH
// =========================================================

function debouncedSearch() {
    clearTimeout(searchTimeout);
    searchTimeout = setTimeout(runSearch, 400);
}

async function runSearch() {
    const q = document.getElementById("dirSearch").value;
    const role = document.getElementById("filterRole").value;
    const batch = document.getElementById("filterBatch").value;
    const tbody = document.getElementById("tableBody");

    tbody.innerHTML = `<tr><td colspan="5" class="text-center py-4"><div class="spinner-border text-primary"></div></td></tr>`;

    try {
        const queryParams = new URLSearchParams({ role, batch, q }).toString();
        const res = await fetch(`${API_URL}/users/search?${queryParams}`, {
            headers: { "Authorization": `Bearer ${token}` }
        });

        if(res.ok) {
            const users = await res.json();
            renderDirectoryTable(users);
        }
    } catch(e) { console.error(e); }
}

function renderDirectoryTable(users) {
    const tbody = document.getElementById("tableBody");
    tbody.innerHTML = "";

    if (users.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5" class="text-center py-5">No users found.</td></tr>`;
        return;
    }

    users.forEach(u => {
        if (u.email === currentUserEmail) return; // Don't show myself
        const initial = u.name.charAt(0).toUpperCase();

        // 1. Admin Edit Button
        let extraBtns = "";
        if (currentUserRole === 'ADMIN') {
            extraBtns = ` <button class="btn btn-sm btn-outline-dark ms-1" title="Edit User" onclick='event.stopPropagation(); openEditUserModal(${JSON.stringify(u)})'><i class="fas fa-edit"></i></button>`;
        }

        // 2. Message Button (Triggers Chat)
        // Important: event.stopPropagation() prevents the row click event
        let msgBtn = `<button class="btn btn-sm btn-outline-primary" title="Message"
                        onclick="event.stopPropagation(); startDirectChat('${u.id}', '${u.name}', '${u.email}')">
                        <i class="fas fa-paper-plane"></i>
                      </button>`;

        tbody.innerHTML += `
            <tr style="cursor:pointer" onclick='openUserProfile(${JSON.stringify(u)})'>
                <td class="ps-3">
                    <div class="avatar-circle small rounded-circle bg-light border d-flex justify-content-center align-items-center" style="width:30px;height:30px;">
                        ${initial}
                    </div>
                </td>
                <td>
                    <div class="fw-bold text-dark">${u.name}</div>
                    <small class="text-muted">${u.headline||u.role}</small>
                </td>
                <td><span class="badge ${u.role==='ALUMNI'?'bg-success':'bg-primary'}">${u.role}</span></td>
                <td class="text-end pe-3">${msgBtn} ${extraBtns}</td>
            </tr>`;
    });
}

function openUserProfile(user) {
    selectedUserForModal = user;
    // Populate Modal
    document.getElementById("modalName").innerText = user.name;
    document.getElementById("modalAvatar").innerText = user.name.charAt(0).toUpperCase();
    document.getElementById("modalRole").innerText = user.role;
    document.getElementById("modalHeadline").innerText = user.headline || "No Headline";
    document.getElementById("modalCompany").innerText = user.currentCompany || "-";
    document.getElementById("modalSkills").innerText = user.skills || "-";

    // Show Modal
    new bootstrap.Modal(document.getElementById('userProfileModal')).show();
}

function openPrivateChatFromProfile() {
    bootstrap.Modal.getInstance(document.getElementById('userProfileModal')).hide();
    if(selectedUserForModal) {
        startDirectChat(selectedUserForModal.id, selectedUserForModal.name, selectedUserForModal.email);
    }
}

// =========================================================
// 7. MODULE: EVENTS
// =========================================================

async function fetchEvents() {
    const container = document.getElementById("eventsContainer");
    if(container) container.innerHTML = '<p class="text-center mt-4">Loading events...</p>';

    try {
        const response = await fetch(`${API_URL}/events`, {
            headers: { "Authorization": `Bearer ${token}` }
        });

        if (response.ok) {
            const events = await response.json();
            renderEvents(events);
        } else {
            container.innerHTML = `<p class="text-center text-danger mt-4">Server Error</p>`;
        }
    } catch (e) { console.error(e); }
}

function renderEvents(events) {
    const container = document.getElementById("eventsContainer");
    if(!container) return;
    container.innerHTML = "";

    if (events.length === 0) {
        container.innerHTML = "<div class='col-12 text-center text-muted'>No upcoming events.</div>";
        return;
    }

    events.forEach(e => {
        const dateStr = new Date(e.dateTime).toDateString();

        // Admin: Edit/Delete buttons
        let adminControls = "";
        if (currentUserRole === "ADMIN") {
            const safeEvent = JSON.stringify(e).replace(/"/g, '&quot;');
            adminControls = `
                <div class="mt-2 pt-2 border-top">
                    <button class="btn btn-sm btn-warning me-1" onclick='openEditEvent(${safeEvent})'><i class="fas fa-edit"></i></button>
                    <button class="btn btn-sm btn-info me-1" onclick="viewParticipants(${e.id})"><i class="fas fa-users"></i></button>
                    <button class="btn btn-sm btn-danger float-end" onclick="deleteEvent(${e.id})"><i class="fas fa-trash"></i></button>
                </div>`;
        }

        let rsvpBtn = e.attending
            ? `<button class="btn btn-success w-100" onclick="toggleRSVP(${e.id})">Going</button>`
            : `<button class="btn btn-outline-primary w-100" onclick="toggleRSVP(${e.id})">RSVP</button>`;

        container.innerHTML += `
            <div class="col-md-4 mb-4">
                <div class="card h-100 shadow-sm">
                    <div class="card-body d-flex flex-column">
                        <div class="d-flex justify-content-between mb-2">
                            <h5 class="card-title text-primary">${e.title}</h5>
                            <small>${dateStr}</small>
                        </div>
                        <h6 class="text-muted small"><i class="fas fa-map-marker-alt"></i> ${e.location}</h6>
                        <p class="flex-grow-1">${e.description}</p>
                        <div class="mt-3">${rsvpBtn}</div>
                        ${adminControls}
                    </div>
                </div>
            </div>`;
    });
}

async function publishNewEvent() {
    const data = {
        title: document.getElementById("evtTitle").value,
        description: document.getElementById("evtDesc").value,
        location: document.getElementById("evtLoc").value,
        dateTime: document.getElementById("evtDate").value
    };
    await fetch(`${API_URL}/events`, {
        method: "POST",
        headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify(data)
    });
    bootstrap.Modal.getInstance(document.getElementById('eventModal')).hide();
    fetchEvents();
}

async function toggleRSVP(id) {
    await fetch(`${API_URL}/events/${id}/rsvp`, {
        method: "POST",
        headers: { "Authorization": `Bearer ${token}` }
    });
    fetchEvents();
}

async function deleteEvent(id) {
    if(confirm("Are you sure?")) {
        await fetch(`${API_URL}/events/${id}`, {
            method: "DELETE",
            headers: { "Authorization": `Bearer ${token}` }
        });
        fetchEvents();
    }
}

// Edit Event Logic
function openEditEvent(event) {
    document.getElementById("editEventId").value = event.id;
    document.getElementById("editEventTitle").value = event.title;
    document.getElementById("editEventDesc").value = event.description;
    document.getElementById("editEventLoc").value = event.location;
    document.getElementById("editEventDate").value = event.dateTime; // Format might need adjustment (ISO)
    new bootstrap.Modal(document.getElementById('editEventModal')).show();
}

async function submitEditEvent() {
    const id = document.getElementById("editEventId").value;
    const data = {
        title: document.getElementById("editEventTitle").value,
        description: document.getElementById("editEventDesc").value,
        location: document.getElementById("editEventLoc").value,
        dateTime: document.getElementById("editEventDate").value
    };
    await fetch(`${API_URL}/events/${id}`, {
        method: "PUT",
        headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify(data)
    });
    bootstrap.Modal.getInstance(document.getElementById('editEventModal')).hide();
    fetchEvents();
}

// =========================================================
// 8. MODULE: JOBS
// =========================================================

async function fetchJobs() {
    try {
        const res = await fetch(`${API_URL}/jobs`, { headers: { "Authorization": `Bearer ${token}` } });
        if(res.ok) {
            const jobs = await res.json();
            renderJobs(jobs);
        }
    } catch(e) { console.error(e); }
}

function renderJobs(jobs) {
    const list = document.getElementById("jobList");
    if(!list) return;
    list.innerHTML = "";

    if(jobs.length === 0) {
        list.innerHTML = "<div class='text-center text-muted'>No jobs posted yet.</div>";
        return;
    }

    jobs.forEach(j => {
        let deleteBtn = (currentUserRole === "ADMIN") ?
            `<button class="btn btn-sm btn-danger float-end ms-2" onclick="deleteJob(${j.id})"><i class="fas fa-trash"></i></button>` : "";

        list.innerHTML += `
            <div class="card p-4 mb-3 shadow-sm job-card">
                <div class="d-flex justify-content-between align-items-center">
                    <h5 class="fw-bold mb-0">${j.title}</h5>
                    <div>${deleteBtn}</div>
                </div>
                <h6 class="text-muted mt-1 mb-2">${j.company} &bull; ${j.location || 'Remote'}</h6>
                <p class="mb-3">${j.description}</p>
                <a href="${j.applyLink}" target="_blank" class="btn btn-sm btn-outline-primary" style="width: fit-content;">Apply Now</a>
            </div>`;
    });
}

async function postJob() {
    const data = {
        title: document.getElementById("jobTitle").value,
        company: document.getElementById("jobCompany").value,
        location: document.getElementById("jobLocation").value,
        description: document.getElementById("jobDesc").value,
        applyLink: document.getElementById("jobLink").value
    };
    await fetch(`${API_URL}/jobs`, {
        method: "POST",
        headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify(data)
    });
    bootstrap.Modal.getInstance(document.getElementById('postJobModal')).hide();
    fetchJobs();
}

async function deleteJob(id) {
    if(confirm("Delete this job?")) {
        await fetch(`${API_URL}/jobs/${id}`, {
            method: "DELETE",
            headers: { "Authorization": `Bearer ${token}` }
        });
        fetchJobs();
    }
}

// =========================================================
// 9. MODULE: WALL / POSTS
// =========================================================

async function fetchPosts() {
    try {
        const res = await fetch(`${API_URL}/posts`, { headers: { "Authorization": `Bearer ${token}` } });
        if (res.ok) {
            const posts = await res.json();
            const container = document.getElementById("wallContainer");
            if(container) {
                if(posts.length === 0) container.innerHTML = "<p class='text-center text-muted'>No posts yet.</p>";
                else container.innerHTML = posts.map(p => `
                    <div class="card mb-3 shadow-sm"><div class="card-body">
                        <div class="d-flex align-items-center mb-2">
                            <div class="avatar-circle small bg-secondary text-white rounded-circle me-2 d-flex justify-content-center align-items-center" style="width:35px;height:35px">${p.authorName.charAt(0)}</div>
                            <strong>${p.authorName}</strong>
                        </div>
                        <p class="card-text">${p.content}</p>
                        ${p.imageUrl ? `<img src="${p.imageUrl}" class="img-fluid rounded mt-2">` : ''}
                    </div></div>`).join('');
            }
        }
    } catch(e) { console.error(e); }
}

async function createPost() {
    const content = document.getElementById("postContent").value;
    const imageUrl = document.getElementById("postImage").value;

    if(!content) return alert("Please write something.");

    await fetch(`${API_URL}/posts`, {
        method: "POST",
        headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ content, imageUrl })
    });

    document.getElementById("postContent").value = "";
    document.getElementById("postImage").value = "";
    fetchPosts();
}

// =========================================================
// 10. MODULE: PROFILE MANAGEMENT
// =========================================================

async function loadProfile() {
    const res = await fetch(`${API_URL}/users/me`, { headers: { "Authorization": `Bearer ${token}` } });
    if(res.ok) {
        const user = await res.json();
        const setVal = (id, val) => { if(document.getElementById(id)) document.getElementById(id).value = val || ""; };

        setVal("pHeadline", user.headline);
        setVal("pCompany", user.currentCompany);
        setVal("pDesignation", user.designation);
        setVal("pSkills", user.skills);
        setVal("pExperience", user.pastExperience);
        setVal("pLinkedin", user.linkedinUrl);
        setVal("pGithub", user.githubUrl);
    }
}

async function saveProfile() {
    const data = {
        headline: document.getElementById("pHeadline").value,
        currentCompany: document.getElementById("pCompany").value,
        designation: document.getElementById("pDesignation").value,
        skills: document.getElementById("pSkills").value,
        pastExperience: document.getElementById("pExperience").value,
        linkedinUrl: document.getElementById("pLinkedin").value,
        githubUrl: document.getElementById("pGithub").value
    };
    const res = await fetch(`${API_URL}/users/profile`, {
        method: "PUT",
        headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify(data)
    });
    alert(res.ok ? "✅ Profile Updated!" : "❌ Failed to update");
}

// =========================================================
// 11. MODULE: ADMIN PANEL
// =========================================================

function loadAdminModule() {
    if (currentUserRole !== 'ADMIN') return;
    loadAdminUsers();

    // Add Event Listeners for sub-tabs
    setTimeout(() => {
        const feedbackBtn = document.getElementById('tab-feedback-btn');
        if(feedbackBtn) feedbackBtn.onclick = loadAdminFeedback;

        const usersBtn = document.getElementById('tab-users-btn');
        if(usersBtn) usersBtn.onclick = loadAdminUsers;
    }, 500);
}

// A. Manage Users
async function loadAdminUsers() {
    const res = await fetch(`${API_URL}/users`, { headers: { "Authorization": `Bearer ${token}` } });
    if(res.ok) {
        const users = await res.json();
        const tbody = document.getElementById("adminUserTable");
        if(tbody) {
            tbody.innerHTML = users.map(u => `
                <tr>
                    <td>${u.id}</td>
                    <td>${u.name}</td>
                    <td>${u.role}</td>
                    <td>${u.email}</td>
                    <td>
                        <button class="btn btn-sm btn-outline-primary me-1" onclick='openEditUserModal(${JSON.stringify(u)})'><i class="fas fa-edit"></i></button>
                        <button class="btn btn-sm btn-outline-danger" onclick="deleteUser(${u.id})"><i class="fas fa-trash"></i></button>
                    </td>
                </tr>`).join('');
        }
    }
}

async function deleteUser(id) {
    if(!confirm("Are you sure? This cannot be undone.")) return;
    const res = await fetch(`${API_URL}/admin/delete-user/${id}`, {
        method: "DELETE",
        headers: { "Authorization": `Bearer ${token}` }
    });
    if(res.ok) loadAdminUsers();
}

function openEditUserModal(user) {
    document.getElementById("editUserId").value = user.id;
    document.getElementById("editName").value = user.name || "";
    document.getElementById("editEmail").value = user.email || "";
    document.getElementById("editRole").value = user.role || "STUDENT";
    document.getElementById("editBatch").value = user.batchYear || "";
    document.getElementById("editEnrollment").value = user.enrollmentNumber || "";
    new bootstrap.Modal(document.getElementById('editUserModal')).show();
}

async function adminSaveUser() {
    const id = document.getElementById("editUserId").value;
    const data = {
        name: document.getElementById("editName").value,
        email: document.getElementById("editEmail").value,
        role: document.getElementById("editRole").value,
        batchYear: document.getElementById("editBatch").value,
        enrollmentNumber: document.getElementById("editEnrollment").value,
    };
    await fetch(`${API_URL}/admin/users/${id}`, {
        method: "PUT",
        headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify(data)
    });
    bootstrap.Modal.getInstance(document.getElementById('editUserModal')).hide();
    loadAdminUsers();
}

// B. Bulk Upload
async function uploadBulkDataExcel() {
    const fileInput = document.getElementById("bulkFileExcel");
    const file = fileInput.files[0];
    if (!file) return alert("⚠️ Please select an Excel file (.xlsx)!");

    const formData = new FormData();
    formData.append("file", file);

    const btn = document.querySelector("button[onclick='uploadBulkDataExcel()']");
    if(btn) { btn.innerHTML = "Processing..."; btn.disabled = true; }

    try {
        const res = await fetch(`${API_URL}/admin/upload-users-excel`, {
            method: "POST",
            headers: { "Authorization": `Bearer ${token}` },
            body: formData
        });
        const text = await res.text();
        alert(res.ok ? text : "❌ Upload Failed: " + text);
        if(res.ok) { fileInput.value = ""; loadAdminUsers(); }
    } catch (e) { alert("❌ Error: " + e.message); }
    finally { if(btn) { btn.innerHTML = "Upload .xlsx"; btn.disabled = false; } }
}

// C. Feedback
async function loadAdminFeedback() {
    const tbody = document.getElementById("adminFeedbackTable");
    if(!tbody) return;
    tbody.innerHTML = "<tr><td colspan='3'>Loading...</td></tr>";

    try {
        const res = await fetch(`${API_URL}/feedback`, { headers: { "Authorization": `Bearer ${token}` } });
        if(res.ok) {
            const feedbacks = await res.json();
            tbody.innerHTML = "";
            if(feedbacks.length === 0) tbody.innerHTML = "<tr><td colspan='3'>No feedback yet.</td></tr>";
            feedbacks.forEach(f => {
                tbody.innerHTML += `<tr><td>${f.submittedBy.name}</td><td class="text-warning">${"⭐".repeat(f.rating)}</td><td>${f.comments}</td></tr>`;
            });
        }
    } catch(e) { tbody.innerHTML = "<tr><td colspan='3'>Error loading feedback</td></tr>"; }
}

// =========================================================
// 12. MODULE: AI BOT
// =========================================================

function toggleBot() {
    const win = document.getElementById("botWindow");
    if(win) win.style.display = win.style.display === "none" ? "block" : "none";
}

async function askBot() {
    const input = document.getElementById("botInput");
    const q = input.value.trim();
    if(!q) return;

    addBotMsg(q, 'my-message');
    input.value = "";

    const typingId = "bot-typing-" + Date.now();
    document.getElementById("botMessages").innerHTML += `<div id="${typingId}" class="message other-message bg-light text-muted small p-1">Typing...</div>`;

    try {
        const res = await fetch(`${API_URL}/bot/ask`, {
            method: "POST",
            headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" },
            body: JSON.stringify({ question: q })
        });

        document.getElementById(typingId)?.remove();

        const data = await res.json();
        addBotMsg(data.answer, 'other-message');
    } catch(e) {
        document.getElementById(typingId)?.remove();
        addBotMsg("⚠️ Server Error: " + e.message, 'other-message');
    }
}

function addBotMsg(txt, cls) {
    const area = document.getElementById("botMessages");
    area.innerHTML += `<div class="message ${cls}" style="max-width:90%; padding:8px; margin-bottom:5px; border-radius:5px; background:${cls==='my-message'?'#e1ffc7':'#f1f0f0'}">${txt}</div>`;
    area.scrollTop = area.scrollHeight;
}