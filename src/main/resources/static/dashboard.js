/**
 * DASHBOARD.JS - Main Application Logic
 * Integrates: Chat, Events, Jobs, Wall, Directory, Profile, and Admin Panel
 */

// =========================================================
// 1. CONFIGURATION & GLOBAL STATE
// =========================================================
// ✅ FIX: Set base path to /api so all requests become /api/users, /api/events, etc.
const API_URL = "/api";
const WS_URL = "/ws"; // WebSockets usually remain at root

// Auth & User State
const token = localStorage.getItem("jwt_token");
let currentUserEmail = localStorage.getItem("user_email");
let currentUserName = localStorage.getItem("user_name");
let currentUserRole = localStorage.getItem("user_role"); // 'STUDENT', 'ALUMNI', 'ADMIN'
let currentUserId = null;

// Chat State
let stompClient = null;
let currentChatPartner = null;

// UI State
let searchTimeout = null;
let selectedUserForModal = null;

// =========================================================
// 2. MAIN STARTUP (DOMContentLoaded)
// =========================================================
document.addEventListener("DOMContentLoaded", async () => {
    try {
        // 1. Authentication Check
        if (!token) {
            console.warn("No token found, redirecting to login.");
            window.location.href = "login.html";
            return;
        }

        // 2. Initial UI Setup
        document.getElementById("welcomeUser").innerText = currentUserName || "User";
        setupAdminVisibility();

        // 3. Ensure we have full user details (Fetch if missing from LocalStorage)
        await ensureUserData();

        // 4. Connect to Real-time Chat
        connectToChat();

        // 5. Load Default Tab (Messages)
        await switchTab('messages');

    } catch (error) {
        console.error("Critical Startup Error:", error);
    } finally {
        hideSpinner();
    }
});

// =========================================================
// 3. HELPER FUNCTIONS (Spinner, Auth, Nav)
// =========================================================

function hideSpinner() {
    const ids = ["loading", "spinner", "loader", "loading-overlay"];
    ids.forEach(id => {
        const el = document.getElementById(id);
        if (el) el.style.display = "none";
    });
}

function setupAdminVisibility() {
    // Show Admin Tab if eligible
    if (currentUserRole === 'ADMIN') {
        const adminTab = document.getElementById("tab-admin");
        if (adminTab) adminTab.classList.remove("d-none");
    }
    // Show Post Job button if eligible
    if (currentUserRole === 'ADMIN' || currentUserRole === 'ALUMNI') {
        const btn = document.getElementById("btnPostJob");
        if(btn) btn.classList.remove("d-none");
    }
}

async function ensureUserData() {
    if (!currentUserEmail) {
        try {
            // ✅ Fix: Uses /api/users/me
            const res = await fetch(`${API_URL}/users/me`, { headers: { "Authorization": `Bearer ${token}` } });
            if (res.ok) {
                const user = await res.json();
                currentUserEmail = user.email;
                currentUserName = user.name;
                currentUserRole = user.role;
                currentUserId = user.id;

                // Update LocalStorage for next time
                localStorage.setItem("user_email", user.email);
                localStorage.setItem("user_name", user.name);
                localStorage.setItem("user_role", user.role);
            }
        } catch (e) {
            console.warn("Could not fetch user details:", e);
        }
    }
}

function logout() {
    localStorage.clear();
    window.location.href = "login.html";
}

// =========================================================
// 4. DYNAMIC TAB NAVIGATION
// =========================================================
async function switchTab(tab) {
    // 1. UI: Active Class on Navbar
    document.querySelectorAll('.nav-link').forEach(btn => btn.classList.remove('active'));
    const btn = document.getElementById(`tab-${tab}`);
    if (btn) btn.classList.add('active');

    // 2. UI: Load HTML Component
    const container = document.getElementById("main-content");
    container.innerHTML = `<div class="text-center py-5"><div class="spinner-border text-primary"></div></div>`;

    try {
        // Note: HTML components are static files, so NO ${API_URL} here.
        const response = await fetch(`components/${tab}.html?v=${Date.now()}`);
        if (!response.ok) throw new Error(`Component '${tab}' not found`);

        const html = await response.text();
        container.innerHTML = html;

        // 3. LOGIC: Initialize specific module logic based on tab
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
                // Show "Add Event" button only for Admins
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
// 5. CHAT SYSTEM (WebSocket + Stomp)
// =========================================================
function connectToChat() {
    const socket = new SockJS(`${WS_URL}`);
    stompClient = Stomp.over(socket);
    stompClient.debug = null;

    stompClient.connect({}, function (frame) {
        console.log('✅ WebSocket Connected');

        // 1. Public Chat
        stompClient.subscribe('/topic/public', function (msg) {
            displayGlobalMsg(JSON.parse(msg.body));
        });

        // 2. Private Messages
        stompClient.subscribe('/user/queue/messages', function (msg) {
            const message = JSON.parse(msg.body);
            if (currentChatPartner && (message.senderEmail === currentChatPartner || message.senderEmail === currentUserEmail)) {
                displayPrivateMsg(message);
            } else {
                console.log(`📩 New message from ${message.senderName}`);
            }
        });

    }, function(error) {
        console.error("❌ Chat connection error (Retrying in 5s): " + error);
        setTimeout(connectToChat, 5000);
    });
}

// --- Public Chat ---
function sendGlobalMessage() {
    const input = document.getElementById("messageInput");
    if (input && input.value.trim() && stompClient) {
        const chatMessage = {
            sender: currentUserEmail,
            senderName: currentUserName,
            content: input.value,
            type: 'CHAT'
        };
        stompClient.send("/app/chat.sendMessage", {}, JSON.stringify(chatMessage));
        input.value = '';
    }
}

function displayGlobalMsg(msg) {
    const area = document.getElementById("chatMessages");
    if (!area) return;

    const isMe = msg.sender === currentUserEmail || msg.senderName === currentUserName;

    const div = document.createElement('div');
    div.className = `message mb-2 p-2 rounded ${isMe ? 'bg-primary text-white ms-auto' : 'bg-light border me-auto'}`;
    div.style.maxWidth = "75%";
    div.innerHTML = `<strong>${msg.senderName || 'User'}:</strong> ${msg.content}`;

    area.appendChild(div);
    area.scrollTop = area.scrollHeight;
}

// --- Private Chat ---
async function fetchRecentChats() {
    try {
        // ✅ Fix: Uses /api/messages/partners
        const res = await fetch(`${API_URL}/messages/partners`, { headers: { "Authorization": `Bearer ${token}` } });
        if(res.ok) {
            const users = await res.json();
            const list = document.getElementById("recentChatsList");
            if(!list) return;

            list.innerHTML = "";
            if(users.length === 0) list.innerHTML = `<div class="text-center py-5 text-muted">No chats yet.</div>`;

            users.forEach(u => {
                const initial = u.name.charAt(0).toUpperCase();
                list.innerHTML += `
                    <div class="chat-item d-flex align-items-center p-3 border-bottom list-group-item-action" style="cursor:pointer" onclick='openChatWithUser(${JSON.stringify(u)})'>
                        <div class="avatar-circle me-3 bg-secondary text-white rounded-circle d-flex align-items-center justify-content-center" style="width:40px;height:40px">${initial}</div>
                        <div>
                            <div class="fw-bold text-dark">${u.name}</div>
                            <small class="text-muted">${u.headline||u.role}</small>
                        </div>
                    </div>`;
            });
        }
    } catch (e) { console.error("Error fetching chats", e); }
}

function openChatWithUser(user) {
    currentChatPartner = user.email;
    const title = document.getElementById("privChatTitle");
    if(title) title.innerText = "Chat with " + user.name;

    const modalEl = document.getElementById('privateChatModal');
    if(modalEl) {
        const modal = new bootstrap.Modal(modalEl);
        modal.show();
        loadChatHistory(currentChatPartner);
    }
}

async function loadChatHistory(partner) {
    const area = document.getElementById("privateChatArea");
    if(!area) return;
    area.innerHTML = "<div class='text-center small text-muted mt-2'>Loading history...</div>";

    try {
        // ✅ Fix: Uses /api/messages/history
        const res = await fetch(`${API_URL}/messages/history?partnerEmail=${partner}`, { headers: { "Authorization": `Bearer ${token}` } });
        if(res.ok) {
            const msgs = await res.json();
            area.innerHTML = "";
            msgs.forEach(m => displayPrivateMsg(m));
        }
    } catch(e) {
        area.innerHTML = "<div class='text-danger text-center'>Failed to load history</div>";
    }
}

function sendPrivateMessage() {
    const input = document.getElementById("privMsgInput");
    const content = input.value.trim();
    if(!content) return;

    // Send via Websocket (No API prefix needed for STOMP destinations usually)
    stompClient.send("/app/chat.private", {}, JSON.stringify({
        senderName: currentUserEmail,
        receiverName: currentChatPartner,
        content: content,
        type: 'CHAT'
    }));

    displayPrivateMsg({ senderName: currentUserEmail, content: content, timestamp: new Date() });
    input.value = "";
}

function displayPrivateMsg(msg) {
    const area = document.getElementById("privateChatArea");
    if (!area) return;

    const msgId = msg.id || `temp-${new Date().getTime()}`;
    if (document.getElementById(`msg-${msgId}`)) return;

    const isMe = msg.senderName === currentUserEmail || msg.senderEmail === currentUserEmail;

    const div = document.createElement("div");
    div.id = `msg-${msgId}`;
    div.className = `d-flex ${isMe ? 'justify-content-end' : 'justify-content-start'} mb-2`;

    const bubble = document.createElement("div");
    bubble.className = `p-2 rounded ${isMe ? 'bg-primary text-white' : 'bg-light border text-dark'}`;
    bubble.style.maxWidth = "70%";
    bubble.innerText = msg.content;

    div.appendChild(bubble);
    area.appendChild(div);
    area.scrollTop = area.scrollHeight;
}

// =========================================================
// 6. MODULE: EVENTS
// =========================================================

async function fetchEvents() {
    console.log("Fetching events...");
    const container = document.getElementById("eventsContainer");

    // 1. Show Loading State
    if(container) container.innerHTML = '<p class="text-center mt-4">Loading events...</p>';

    // 2. Get Token safely
    const token = localStorage.getItem("jwtToken");

    try {
        // ✅ FIX: Added '/api' prefix
        const response = await fetch('/api/events', {
            headers: {
                "Authorization": `Bearer ${token}`,
                "Content-Type": "application/json"
            }
        });

        if (response.ok) {
            const events = await response.json();
            renderEvents(events);
        } else {
            console.error("Server Error:", response.status);
            if(container) container.innerHTML = `<p class="text-center text-danger mt-4">Server Error: ${response.status}</p>`;
        }
    } catch (error) {
        console.error("Error loading events:", error);
        if(container) container.innerHTML = '<p class="text-center text-danger mt-4">Network Error.</p>';
    }
}

function renderEvents(events) {
    const container = document.getElementById("eventsContainer");
    if(!container) return;
    container.innerHTML = "";

    if(events.length === 0) {
        container.innerHTML = "<div class='col-12 text-center text-muted'>No upcoming events.</div>";
        return;
    }

    // Get current user role safely
    const currentUserRole = localStorage.getItem("role"); // or however you store it

    events.forEach(e => {
        const dateStr = new Date(e.dateTime).toDateString();

        // Admin Controls
        let adminControls = "";
        if (currentUserRole === "ADMIN") {
            // Escape quotes in the object to prevent HTML breaking
            const safeEvent = JSON.stringify(e).replace(/"/g, '&quot;');
            adminControls = `
                <div class="mt-2 pt-2 border-top">
                    <button class="btn btn-sm btn-warning me-1" onclick='openEditEvent(${safeEvent})'><i class="fas fa-edit"></i></button>
                    <button class="btn btn-sm btn-info me-1" onclick="viewParticipants(${e.id})"><i class="fas fa-users"></i></button>
                    <button class="btn btn-sm btn-danger float-end" onclick="deleteEvent(${e.id})"><i class="fas fa-trash"></i></button>
                </div>`;
        }

        let rsvpBtn = e.attending
            ? `<button class="btn btn-success w-100" onclick="toggleRSVP(${e.id})"><i class="fas fa-check"></i> Going</button>`
            : `<button class="btn btn-outline-primary w-100" onclick="toggleRSVP(${e.id})">RSVP / Join</button>`;

        container.innerHTML += `
            <div class="col-md-4 mb-4">
                <div class="card event-card h-100 shadow-sm">
                    <div class="card-body d-flex flex-column">
                        <div class="d-flex justify-content-between align-items-start mb-2">
                            <h5 class="card-title text-primary fw-bold">${e.title}</h5>
                            <span class="badge bg-light text-dark border">${dateStr}</span>
                        </div>
                        <h6 class="text-muted small mb-3"><i class="fas fa-map-marker-alt"></i> ${e.location}</h6>
                        <p class="card-text flex-grow-1">${e.description}</p>
                        <div class="mt-3">
                            ${rsvpBtn}
                            <div class="text-muted small text-center mt-1">${e.participantCount || 0} attending</div>
                        </div>
                        ${adminControls}
                    </div>
                </div>
            </div>`;
    });
}

async function publishNewEvent() {
    const token = localStorage.getItem("jwtToken");
    const data = {
        title: document.getElementById("evtTitle").value,
        description: document.getElementById("evtDesc").value,
        location: document.getElementById("evtLoc").value,
        dateTime: document.getElementById("evtDate").value
    };

    // ✅ FIX: Added '/api' prefix
    const res = await fetch('/api/events', {
        method: "POST",
        headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify(data)
    });

    if(res.ok) {
        bootstrap.Modal.getInstance(document.getElementById('eventModal')).hide();
        fetchEvents();
    } else {
        alert("Failed to create event");
    }
}

async function toggleRSVP(eventId) {
    const token = localStorage.getItem("jwtToken");
    // ✅ FIX: Added '/api' prefix
    const res = await fetch(`/api/events/${eventId}/rsvp`, {
        method: "POST",
        headers: { "Authorization": `Bearer ${token}` }
    });
    if(res.ok) fetchEvents();
}

async function deleteEvent(id) {
    const token = localStorage.getItem("jwtToken");
    if(confirm("Are you sure you want to delete this event?")) {
        // ✅ FIX: Added '/api' prefix
        await fetch(`/api/events/${id}`, {
            method: "DELETE",
            headers: { "Authorization": `Bearer ${token}` }
        });
        fetchEvents();
    }
}

async function viewParticipants(eventId) {
    const token = localStorage.getItem("jwtToken");
    // ✅ FIX: Added '/api' prefix
    const res = await fetch(`/api/events/${eventId}/participants`, {
        headers: { "Authorization": `Bearer ${token}` }
    });

    if(res.ok) {
        const names = await res.json();
        const list = document.getElementById("participantsList");
        list.innerHTML = names.length ? names.map(n => `<li class="list-group-item">${n}</li>`).join("") : "<li class='list-group-item text-muted'>No participants yet</li>";
        new bootstrap.Modal(document.getElementById('participantsModal')).show();
    }
}

// (Keep your openEditEvent and submitEditEvent functions, just make sure submitEditEvent uses /api/events/${id})
async function submitEditEvent() {
    const token = localStorage.getItem("jwtToken");
    const id = document.getElementById("editEventId").value;
    const data = {
        title: document.getElementById("editEventTitle").value,
        description: document.getElementById("editEventDesc").value,
        location: document.getElementById("editEventLoc").value,
        dateTime: document.getElementById("editEventDate").value
    };
    // ✅ FIX: Added '/api' prefix
    await fetch(`/api/events/${id}`, {
        method: "PUT",
        headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify(data)
    });
    bootstrap.Modal.getInstance(document.getElementById('editEventModal')).hide();
    fetchEvents();
}
// =========================================================
// 7. MODULE: JOBS
// =========================================================
async function fetchJobs() {
    try {
        // ✅ Fix: Uses /api/jobs
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
    // ✅ Fix: Uses /api/jobs
    const res = await fetch(`${API_URL}/jobs`, { method: "POST", headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" }, body: JSON.stringify(data) });
    if(res.ok) {
        bootstrap.Modal.getInstance(document.getElementById('postJobModal')).hide();
        fetchJobs();
    } else {
        alert("Failed to post job");
    }
}

async function deleteJob(id) {
    if(confirm("Admin: Delete this job?")) {
        await fetch(`${API_URL}/jobs/${id}`, { method: "DELETE", headers: { "Authorization": `Bearer ${token}` } });
        fetchJobs();
    }
}

// =========================================================
// 8. MODULE: WALL / POSTS
// =========================================================
async function fetchPosts() {
    try {
        // ✅ Fix: Uses /api/posts
        const response = await fetch(`${API_URL}/posts`, { headers: { "Authorization": `Bearer ${token}` } });
        if (response.ok) {
            const posts = await response.json();
            renderPosts(posts);
        } else {
            document.getElementById("wallContainer").innerHTML = "<p class='text-muted p-3'>No posts yet.</p>";
        }
    } catch (error) {
        console.error("Error fetching posts:", error);
    }
}

function renderPosts(posts) {
    const container = document.getElementById("wallContainer");
    if (!container) return;
    container.innerHTML = "";

    if (posts.length === 0) {
        container.innerHTML = "<p class='text-muted text-center py-4'>No posts available.</p>";
        return;
    }

    posts.forEach(post => {
        const name = post.authorName || 'Anonymous';
        const date = post.timestamp ? new Date(post.timestamp).toLocaleString() : 'Just now';
        const imgHtml = post.imageUrl ? `<img src="${post.imageUrl}" class="img-fluid rounded mt-2" style="max-height:300px">` : '';

        container.innerHTML += `
            <div class="card mb-3 shadow-sm">
                <div class="card-body">
                    <div class="d-flex align-items-center mb-2">
                        <div class="avatar-circle small bg-secondary text-white rounded-circle me-2 d-flex justify-content-center align-items-center" style="width:35px;height:35px">${name.charAt(0)}</div>
                        <div>
                            <div class="fw-bold text-dark">${name}</div>
                            <small class="text-muted" style="font-size:0.75rem">${date}</small>
                        </div>
                    </div>
                    <p class="card-text">${post.content}</p>
                    ${imgHtml}
                </div>
            </div>`;
    });
}

async function createPost() {
    const content = document.getElementById("postContent").value;
    const imageUrl = document.getElementById("postImage").value;

    if(!content) return alert("Content cannot be empty");

    // ✅ Fix: Uses /api/posts
    const res = await fetch(`${API_URL}/posts`, {
        method: "POST",
        headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ content, imageUrl })
    });

    if(res.ok) {
        document.getElementById("postContent").value = "";
        document.getElementById("postImage").value = "";
        fetchPosts();
    }
}

// =========================================================
// 9. MODULE: DIRECTORY & SEARCH
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
        // ✅ Fix: Uses /api/users/search
        const res = await fetch(`${API_URL}/users/search?role=${role}&batch=${batch}&q=${encodeURIComponent(q)}`, { headers: { "Authorization": `Bearer ${token}` } });
        if(res.ok) {
            const users = await res.json();
            renderDirectoryTable(users);
        }
    } catch(e) { console.error(e); }
}

function renderDirectoryTable(users) {
    const tbody = document.getElementById("tableBody");
    tbody.innerHTML = "";
    if (users.length === 0) { tbody.innerHTML = `<tr><td colspan="5" class="text-center py-5">No users found.</td></tr>`; return; }

    users.forEach(u => {
        if (u.email === currentUserEmail) return;
        const initial = u.name.charAt(0).toUpperCase();

        let actionBtns = `<button class="btn btn-sm btn-outline-primary" title="Message" onclick="event.stopPropagation(); openChatWithUser(${JSON.stringify(u)})"><i class="fas fa-paper-plane"></i></button>`;
        if (currentUserRole === 'ADMIN') {
            actionBtns += ` <button class="btn btn-sm btn-outline-dark ms-1" title="Edit User" onclick='event.stopPropagation(); openEditUserModal(${JSON.stringify(u)})'><i class="fas fa-edit"></i></button>`;
        }

        tbody.innerHTML += `
            <tr style="cursor:pointer" onclick='openUserProfile(${JSON.stringify(u)})'>
                <td class="ps-3"><div class="avatar-circle small rounded-circle bg-light border d-flex justify-content-center align-items-center" style="width:30px;height:30px;">${initial}</div></td>
                <td><div class="fw-bold text-dark">${u.name}</div><small class="text-muted">${u.headline||u.role}</small></td>
                <td><span class="badge ${u.role==='ALUMNI'?'bg-success':'bg-primary'}">${u.role}</span></td>
                <td class="text-end pe-3">${actionBtns}</td>
            </tr>`;
    });
}

function openUserProfile(user) {
    selectedUserForModal = user;
    document.getElementById("modalName").innerText = user.name;
    document.getElementById("modalAvatar").innerText = user.name.charAt(0).toUpperCase();
    document.getElementById("modalRole").innerText = user.role;
    document.getElementById("modalHeadline").innerText = user.headline || "No Headline";
    document.getElementById("modalCompany").innerText = user.currentCompany || "-";
    document.getElementById("modalSkills").innerText = user.skills || "-";
    new bootstrap.Modal(document.getElementById('userProfileModal')).show();
}

function openPrivateChatFromProfile() {
    bootstrap.Modal.getInstance(document.getElementById('userProfileModal')).hide();
    openChatWithUser(selectedUserForModal);
}

// =========================================================
// 10. MODULE: PROFILE MANAGEMENT
// =========================================================
async function loadProfile() {
    try {
        // ✅ Fix: Uses /api/users/me
        const res = await fetch(`${API_URL}/users/me`, { headers: { "Authorization": `Bearer ${token}` } });
        if (res.ok) {
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
    } catch (e) { console.error(e); }
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
    // ✅ Fix: Uses /api/users/profile
    const res = await fetch(`${API_URL}/users/profile`, { method: "PUT", headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" }, body: JSON.stringify(data) });
    alert(res.ok ? "✅ Profile Updated!" : "❌ Failed to update");
}

// =========================================================
// 11. MODULE: ADMIN PANEL
// =========================================================
function loadAdminModule() {
    if (currentUserRole !== 'ADMIN') return;
    loadAdminUsers();

    // Attach listeners dynamically
    setTimeout(() => {
        const feedbackTab = document.querySelector('button[data-bs-target="#feedback"], a[href="#feedback"], #tab-feedback-btn');
        const usersTab = document.querySelector('button[data-bs-target="#users"], a[href="#users"], #tab-users-btn');

        if(feedbackTab) feedbackTab.onclick = loadAdminFeedback;
        if(usersTab) usersTab.onclick = loadAdminUsers;
    }, 200);
}

async function loadAdminUsers() {
    // ✅ Fix: Uses /api/users
    const res = await fetch(`${API_URL}/users`, { headers: { "Authorization": `Bearer ${token}` } });
    if(res.ok) {
        const users = await res.json();
        const tbody = document.getElementById("adminUserTable");
        if(tbody) {
            tbody.innerHTML = "";
            users.forEach(u => {
                tbody.innerHTML += `
                    <tr>
                        <td>${u.id}</td>
                        <td>${u.name}</td>
                        <td>${u.role}</td>
                        <td>${u.email}</td>
                        <td>
                            <button class="btn btn-sm btn-outline-primary me-1" onclick='openEditUserModal(${JSON.stringify(u)})'><i class="fas fa-edit"></i></button>
                            <button class="btn btn-sm btn-outline-danger" onclick="deleteUser(${u.id})"><i class="fas fa-trash"></i></button>
                        </td>
                    </tr>`;
            });
        }
    }
}

async function deleteUser(id) {
    if(!confirm("Are you sure? This cannot be undone.")) return;
    // ✅ Fix: Uses /api/admin/delete-user
    const res = await fetch(`${API_URL}/admin/delete-user/${id}`, { method: "DELETE", headers: { "Authorization": `Bearer ${token}` } });
    if(res.ok) loadAdminUsers();
}

async function uploadBulkDataExcel() {
    const fileInput = document.getElementById("bulkFileExcel");
    const file = fileInput.files[0];
    if (!file) return alert("⚠️ Please select an Excel file (.xlsx)!");

    const formData = new FormData();
    formData.append("file", file);

    const btn = document.querySelector("button[onclick='uploadBulkDataExcel()']");
    if(btn) { btn.innerHTML = "Processing..."; btn.disabled = true; }

    try {
        // ✅ Fix: Uses /api/admin/upload...
        const res = await fetch(`${API_URL}/admin/upload-users-excel`, { method: "POST", headers: { "Authorization": `Bearer ${token}` }, body: formData });
        const text = await res.text();
        alert(res.ok ? text : "❌ Upload Failed: " + text);
        if(res.ok) { fileInput.value = ""; loadAdminUsers(); }
    } catch (e) { alert("❌ Error: " + e.message); }
    finally { if(btn) { btn.innerHTML = "Upload .xlsx"; btn.disabled = false; } }
}

async function loadAdminFeedback() {
    const tbody = document.getElementById("adminFeedbackTable");
    if(!tbody) return;
    tbody.innerHTML = "<tr><td colspan='3'>Loading...</td></tr>";

    try {
        // ✅ Fix: Uses /api/feedback
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

    // ✅ Fix: Uses /api/admin/users
    const res = await fetch(`${API_URL}/admin/users/${id}`, {
        method: "PUT",
        headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify(data)
    });

    if(res.ok) {
        alert("✅ User Updated!");
        bootstrap.Modal.getInstance(document.getElementById('editUserModal')).hide();
        loadAdminUsers();
    } else {
        alert("❌ Update failed.");
    }
}

async function adminCreateUser() {
    const data = {
        name: document.getElementById("newUserName").value,
        email: document.getElementById("newUserEmail").value,
        role: document.getElementById("newUserRole").value,
        batchYear: document.getElementById("newUserBatch").value,
        password: "placeholder"
    };

    // ✅ Fix: Uses /api/admin/create-user
    const res = await fetch(`${API_URL}/admin/create-user`, { method: "POST", headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" }, body: JSON.stringify(data) });

    if(res.ok) {
        alert("User Created! Default Pass: Bvicam@2025");
        bootstrap.Modal.getInstance(document.getElementById('addUserModal')).hide();
        loadAdminUsers();
    } else { alert("Failed to create user"); }
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

    // Show typing indicator
    const typingId = "bot-typing-" + Date.now();
    document.getElementById("botMessages").innerHTML += `<div id="${typingId}" class="message other-message bg-light text-muted small p-1">Typing...</div>`;

    try {
        // ✅ Fix: Uses /api/bot/ask
        const res = await fetch(`${API_URL}/bot/ask`, { method: "POST", headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" }, body: JSON.stringify({ question: q }) });

        const el = document.getElementById(typingId);
        if(el) el.remove();

        const data = await res.json();
        addBotMsg(data.answer, 'other-message');
    } catch(e) {
        const el = document.getElementById(typingId);
        if(el) el.remove();
        addBotMsg("⚠️ Server Error: " + e.message, 'other-message');
    }
}

function addBotMsg(txt, cls) {
    const area = document.getElementById("botMessages");
    area.innerHTML += `<div class="message ${cls}" style="max-width:90%; padding:8px; margin-bottom:5px; border-radius:5px; background:${cls==='my-message'?'#e1ffc7':'#f1f0f0'}">${txt}</div>`;
    area.scrollTop = area.scrollHeight;
}