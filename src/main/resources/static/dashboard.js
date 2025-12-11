// --- CONFIGURATION ---
const API_URL = "/api";
const WS_URL = "/ws";
const token = localStorage.getItem("jwt_token");
const myEmail = localStorage.getItem("user_email"); // Saved from login
const userName = localStorage.getItem("user_name");
const userRole = localStorage.getItem("user_role");

let stompClient = null;
let currentChatPartner = null;
let searchTimeout = null;
let selectedUserForModal = null; 

// --- INITIALIZATION ---
if (!token) {
    window.location.href = "index.html";
} else {
    document.getElementById("welcomeUser").innerText = userName;

    // Ensure email is stored (Vital for Messaging)
    if (!myEmail) {
        fetch(`${API_URL}/users/me`, { headers: { "Authorization": `Bearer ${token}` } })
            .then(res => res.json())
            .then(user => localStorage.setItem("user_email", user.email));
    }

    // Default to Messages
    switchTab('messages'); 
    connectToChat();
}

// --- DYNAMIC CONTENT LOADING ---
async function switchTab(tab) {
    // 1. Highlight UI
    document.querySelectorAll('.nav-link').forEach(btn => btn.classList.remove('active'));
    const btn = document.getElementById(`tab-${tab}`);
    if(btn) btn.classList.add('active');

    // 2. Load HTML Component
    const container = document.getElementById("main-content");
    container.innerHTML = `<div class="text-center py-5"><div class="spinner-border text-primary"></div></div>`;

    try {
        const response = await fetch(`components/${tab}.html`);
        if(!response.ok) throw new Error("Component file not found");
        const html = await response.text();
        container.innerHTML = html;

        // 3. Trigger Feature Logic
        if(tab === 'messages') fetchRecentChats();
        if(tab === 'directory') runSearch();
        if(tab === 'jobs') {
            fetchJobs();
            if (userRole === "ALUMNI" || userRole === "ADMIN") {
                const postBtn = document.getElementById("btnPostJob");
                if(postBtn) postBtn.classList.remove("d-none");
            }
        }
        if(tab === 'wall') fetchPosts();
        if(tab === 'profile') loadProfile();

        // --- NEW: EVENTS TAB ---
        if(tab === 'events') {
            fetchEvents();
            // Show "Add Event" button only for Admins
            if (userRole === "ADMIN") {
                // Small timeout to ensure DOM is ready
                setTimeout(() => {
                    const btnAdd = document.getElementById("btnAddEvent");
                    if(btnAdd) btnAdd.classList.remove("d-none");
                }, 100);
            }
        }

        // --- NEW: ADMIN TAB ---
        if(tab === 'admin') {
            if(userRole !== 'ADMIN') {
                container.innerHTML = `<div class="alert alert-danger text-center m-5">⛔ Access Denied</div>`;
            } else {
                loadAdminUsers();
            }
        }

    } catch(e) {
        container.innerHTML = `<div class="alert alert-danger">Error: ${e.message}</div>`;
    }
}

function logout() { localStorage.clear(); window.location.href="index.html"; }


// ================= MODULE 1: PROFILE MANAGEMENT =================
async function loadProfile() {
    try {
        const res = await fetch(`${API_URL}/users/me`, { headers: { "Authorization": `Bearer ${token}` } });
        if(res.ok) {
            const user = await res.json();
            if(document.getElementById("pHeadline")) {
                document.getElementById("pHeadline").value = user.headline || "";
                document.getElementById("pCompany").value = user.currentCompany || "";
                document.getElementById("pDesignation").value = user.designation || "";
                document.getElementById("pSkills").value = user.skills || "";
                document.getElementById("pExperience").value = user.pastExperience || "";
                document.getElementById("pLinkedin").value = user.linkedinUrl || "";
                document.getElementById("pGithub").value = user.githubUrl || "";
            }
        }
    } catch(e) { console.error(e); }
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

    if(res.ok) alert("✅ Profile Updated!");
    else alert("❌ Failed to update");
}

// ================= MODULE 2: DIRECTORY & SEARCH =================
function debouncedSearch() { clearTimeout(searchTimeout); searchTimeout = setTimeout(runSearch, 400); }

async function runSearch() {
    const q = document.getElementById("dirSearch").value;
    const role = document.getElementById("filterRole").value;
    const batch = document.getElementById("filterBatch").value;
    
    const tbody = document.getElementById("tableBody");
    tbody.innerHTML = `<tr><td colspan="5" class="text-center py-4"><div class="spinner-border text-primary"></div></td></tr>`;

    try {
        const url = `${API_URL}/users/search?role=${role}&batch=${batch}&q=${encodeURIComponent(q)}`;
        const res = await fetch(url, { headers: { "Authorization": `Bearer ${token}` } });
        if(res.ok) {
            const users = await res.json();
            tbody.innerHTML = "";
            if(users.length === 0) { tbody.innerHTML = `<tr><td colspan="4" class="text-center py-4">No users found.</td></tr>`; return; }
            
            users.forEach(u => {
                if(u.email === myEmail) return;
                const initial = u.name.charAt(0).toUpperCase();
                tbody.innerHTML += `
                    <tr class="user-row" onclick='openUserProfile(${JSON.stringify(u)})'>
                        <td><div class="avatar-circle small">${initial}</div></td>
                        <td><div class="fw-bold text-dark">${u.name}</div><small class="text-muted">${u.headline||u.role}</small></td>
                        <td><span class="badge ${u.role==='ALUMNI'?'bg-success':'bg-primary'}">${u.role}</span></td>
                        <td><button class="btn btn-sm btn-outline-primary"><i class="fas fa-paper-plane"></i></button></td>
                    </tr>`;
            });
        }
    } catch(e) { console.error(e); }
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

// ================= MODULE 3: MESSAGING & CHAT =================
function connectToChat() {
    const socket = new SockJS(WS_URL);
    stompClient = Stomp.over(socket);
    stompClient.debug = null;

    stompClient.connect({}, function () {
        document.getElementById("chatStatus").innerText = "Online";
        document.getElementById("chatStatus").className = "badge bg-success ms-2";
        
        stompClient.subscribe('/topic/public', payload => displayGlobalMsg(JSON.parse(payload.body)));
        
        stompClient.subscribe('/user/queue/messages', payload => {
            const msg = JSON.parse(payload.body);
            if(document.getElementById("recentChatsList")) fetchRecentChats(); 

            if (currentChatPartner && (msg.senderName === currentChatPartner || msg.senderName === myEmail)) {
                displayPrivateMsg(msg);
            } else if (msg.senderName !== myEmail) {
                document.getElementById("msgBadge").classList.remove("d-none");
                const toastBody = document.getElementById("toastBody");
                if(toastBody) {
                    toastBody.innerText = `${msg.senderName}: ${msg.content}`;
                    new bootstrap.Toast(document.getElementById('liveToast')).show();
                }
            }
        });
    }, function() { document.getElementById("chatStatus").innerText = "Offline"; });
}

async function fetchRecentChats() {
    const res = await fetch(`${API_URL}/messages/partners`, { headers: { "Authorization": `Bearer ${token}` } });
    if(res.ok) {
        const users = await res.json();
        const list = document.getElementById("recentChatsList");
        list.innerHTML = "";
        if(users.length === 0) list.innerHTML = `<div class="text-center py-5 text-muted">No chats yet.</div>`;
        users.forEach(u => {
            const initial = u.name.charAt(0).toUpperCase();
            list.innerHTML += `
                <div class="chat-item d-flex align-items-center p-3 border-bottom" onclick='openChatWithUser(${JSON.stringify(u)})'>
                    <div class="avatar-circle me-3">${initial}</div>
                    <div><div class="fw-bold text-dark">${u.name}</div><small class="text-muted">${u.headline||u.role}</small></div>
                </div>`;
        });
    }
}

function openChatWithUser(user) {
    currentChatPartner = user.email;
    document.getElementById("privChatTitle").innerText = "Chat with " + user.name;
    const modal = new bootstrap.Modal(document.getElementById('privateChatModal'));
    modal.show();
    loadChatHistory(currentChatPartner);
}

async function loadChatHistory(partner) {
    document.getElementById("privateChatArea").innerHTML = "<div class='text-center small text-muted mt-2'>Loading...</div>";
    const res = await fetch(`${API_URL}/messages/history?partnerEmail=${partner}`, { headers: { "Authorization": `Bearer ${token}` } });
    if(res.ok) {
        const msgs = await res.json();
        document.getElementById("privateChatArea").innerHTML = "";
        msgs.forEach(m => displayPrivateMsg({ senderName: m.senderEmail, content: m.content }));
    }
}

function sendPrivateMessage() {
    const input = document.getElementById("privMsgInput");
    const content = input.value.trim();
    if(!content) return;
    stompClient.send("/app/chat.private", {}, JSON.stringify({ senderName: myEmail, receiverName: currentChatPartner, content: content, type: 'CHAT' }));
    displayPrivateMsg({ senderName: myEmail, content: content });
    input.value = "";
}

function displayPrivateMsg(msg) {
    const area = document.getElementById("privateChatArea");
    const isMe = msg.senderName === myEmail;
    area.innerHTML += `<div class="message ${isMe?'my-message':'other-message'}">${msg.content}</div>`;
    area.scrollTop = area.scrollHeight;
}

function sendMessage() {
    const input = document.getElementById("messageInput");
    if(input.value.trim()) {
        stompClient.send("/app/chat.sendMessage", {}, JSON.stringify({ senderName: userName, content: input.value, type: 'CHAT' }));
        input.value = "";
    }
}

function displayGlobalMsg(msg) {
    const area = document.getElementById("chatArea");
    area.innerHTML += `<div class="message ${msg.senderName===userName?'my-message':'other-message'}"><b>${msg.senderName}:</b> ${msg.content}</div>`;
    area.scrollTop = area.scrollHeight;
}

// ================= MODULE 4: BOT & JOBS & WALL =================
function toggleBot() {
    const win = document.getElementById("botWindow");
    win.style.display = win.style.display === "none" ? "block" : "none";
}

async function askBot() {
    const input = document.getElementById("botInput");
    const q = input.value.trim();
    if(!q) return;
    addBotMsg(q, 'my-message');
    input.value = "";
    try {
        const res = await fetch(`${API_URL}/bot/ask`, {
            method: "POST",
            headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" },
            body: JSON.stringify({ question: q })
        });
        const data = await res.json();
        addBotMsg(data.answer, 'other-message');
    } catch(e) { addBotMsg("⚠️ Server Error", 'other-message'); }
}

function addBotMsg(txt, cls) {
    const area = document.getElementById("botMessages");
    area.innerHTML += `<div class="message ${cls}" style="max-width:90%;">${txt}</div>`;
    area.scrollTop = area.scrollHeight;
}

async function fetchJobs() {
    const res = await fetch(`${API_URL}/jobs`, { headers: { "Authorization": `Bearer ${token}` } });
    if(res.ok) {
        const jobs = await res.json();
        const list = document.getElementById("jobList");
        list.innerHTML = "";
        jobs.forEach(j => {
            list.innerHTML += `<div class="card p-3 mb-2 shadow-sm job-card"><h5>${j.title}</h5><h6 class="text-muted">${j.company}</h6><p>${j.description}</p><a href="mailto:${j.applyLink}" class="btn btn-sm btn-outline-primary">Apply</a></div>`;
        });
    }
}

async function postJob() {
    const data = {
        title: document.getElementById("jobTitle").value,
        company: document.getElementById("jobCompany").value,
        location: document.getElementById("jobLocation").value,
        description: document.getElementById("jobDesc").value,
        applyLink: document.getElementById("jobLink").value
    };
    const res = await fetch(`${API_URL}/jobs`, { method: "POST", headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" }, body: JSON.stringify(data) });
    if(res.ok) { bootstrap.Modal.getInstance(document.getElementById('postJobModal')).hide(); fetchJobs(); }
}

async function fetchPosts() {
    const res = await fetch(`${API_URL}/posts`, { headers: { "Authorization": `Bearer ${token}` } });
    if(res.ok) {
        const posts = await res.json();
        const feed = document.getElementById("feedArea");
        feed.innerHTML = "";
        posts.forEach(p => {
            const imgHtml = p.imageUrl ? `<img src="${p.imageUrl}" class="img-fluid rounded mt-2" style="max-height:300px">` : '';
            feed.innerHTML += `<div class="card p-3 mb-3 shadow-sm"><div class="fw-bold">${p.author.name}</div><p>${p.content}</p>${imgHtml}</div>`;
        });
    }
}

async function createPost() {
    const res = await fetch(`${API_URL}/posts`, { method: "POST", headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" }, body: JSON.stringify({ content: document.getElementById("postContent").value, imageUrl: document.getElementById("postImage").value }) });
    if(res.ok) fetchPosts();
}

// ================= MODULE 5: EVENTS =================
async function fetchEvents() {
    const res = await fetch(`${API_URL}/events`, { headers: { "Authorization": `Bearer ${token}` } });
    if(res.ok) {
        const events = await res.json();
        const list = document.getElementById("eventsList");
        if(list) {
            list.innerHTML = "";
            if(events.length === 0) list.innerHTML = `<div class="text-center py-5 text-muted">No upcoming events.</div>`;
            
            events.forEach(e => {
                const date = new Date(e.dateTime).toLocaleString();
                list.innerHTML += `
                    <div class="col-md-6 mb-3">
                        <div class="card event-card h-100 shadow-sm p-3">
                            <div class="d-flex justify-content-between">
                                <h5 class="fw-bold text-primary">${e.title}</h5>
                                ${userRole==='ADMIN' ? `<button class="btn btn-sm text-danger" onclick="deleteEvent(${e.id})"><i class="fas fa-trash"></i></button>` : ''}
                            </div>
                            <p class="text-muted small mb-2"><i class="fas fa-clock me-1"></i> ${date} <span class="ms-2"><i class="fas fa-map-marker-alt me-1"></i> ${e.location}</span></p>
                            <p>${e.description}</p>
                        </div>
                    </div>`;
            });
        }
    }
}

// --- FIX: Renamed from createEvent to publishNewEvent ---
async function publishNewEvent() {
    const data = {
        title: document.getElementById("evtTitle").value,
        description: document.getElementById("evtDesc").value,
        location: document.getElementById("evtLoc").value,
        dateTime: document.getElementById("evtDate").value
    };
    const res = await fetch(`${API_URL}/events`, { 
        method: "POST", 
        headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" }, 
        body: JSON.stringify(data) 
    });
    
    if(res.ok) {
        bootstrap.Modal.getInstance(document.getElementById('eventModal')).hide();
        fetchEvents(); // Refresh list
    } else {
        alert("Failed to create event");
    }
}
async function fetchJobs() {
    const res = await fetch(`${API_URL}/jobs`, { headers: { "Authorization": `Bearer ${token}` } });
    if(res.ok) {
        const jobs = await res.json();
        const list = document.getElementById("jobList");
        list.innerHTML = "";
        
        jobs.forEach(j => {
            // FIX: Show Delete Button if ADMIN
            let deleteBtn = "";
            if (userRole === "ADMIN") {
                deleteBtn = `<button class="btn btn-sm btn-danger float-end ms-2" onclick="deleteJob(${j.id})"><i class="fas fa-trash"></i></button>`;
            }

            list.innerHTML += `
                <div class="card p-3 mb-2 shadow-sm job-card">
                    <div class="d-flex justify-content-between">
                        <h5>${j.title}</h5>
                        <div>${deleteBtn}</div>
                    </div>
                    <h6 class="text-muted">${j.company}</h6>
                    <p>${j.description}</p>
                    <a href="mailto:${j.applyLink}" class="btn btn-sm btn-outline-primary">Apply</a>
                </div>`;
        });
    }
}

// Add this new function for deleting jobs
async function deleteJob(id) {
    if(!confirm("Admin: Delete this job?")) return;
    // We reuse the generic delete endpoint if available, or create a specific one
    // Assuming you have DELETE /api/jobs/{id} in JobController
    const res = await fetch(`${API_URL}/jobs/${id}`, { 
        method: "DELETE", 
        headers: { "Authorization": `Bearer ${token}` } 
    });
    if(res.ok) fetchJobs();
}

async function deleteEvent(id) {
    if(!confirm("Delete event?")) return;
    await fetch(`${API_URL}/events/${id}`, { method: "DELETE", headers: { "Authorization": `Bearer ${token}` } });
    fetchEvents();
}

// ================= MODULE 6: ADMIN =================
async function loadAdminUsers() {
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

function openEditUserModal(user) {
    document.getElementById("editUserId").value = user.id;
    document.getElementById("editName").value = user.name;
    document.getElementById("editEmail").value = user.email;
    document.getElementById("editRole").value = user.role;
    document.getElementById("editHeadline").value = user.headline || "";
    document.getElementById("editSkills").value = user.skills || "";
    new bootstrap.Modal(document.getElementById('editUserModal')).show();
}

async function adminSaveUser() {
    const id = document.getElementById("editUserId").value;
    
    const data = {
        name: document.getElementById("editName").value,
        email: document.getElementById("editEmail").value,
        role: document.getElementById("editRole").value,
        headline: document.getElementById("editHeadline").value,
        skills: document.getElementById("editSkills").value
    };
    
    console.log("Admin updating user:", id, data);

    const res = await fetch(`${API_URL}/admin/users/${id}`, { 
        method: "PUT", 
        headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" }, 
        body: JSON.stringify(data) 
    });
    
    if(res.ok) {
        alert("✅ User Updated Successfully!");
        bootstrap.Modal.getInstance(document.getElementById('editUserModal')).hide();
        loadAdminUsers(); // Refresh the Admin Table
    } else {
        alert("❌ Failed to update user. Check console.");
        console.error(await res.text());
    }
}

async function adminCreateUser() {
    const data = {
        name: document.getElementById("adminName").value,
        email: document.getElementById("adminEmail").value,
        role: document.getElementById("adminRole").value,
        password: "placeholder" 
    };

    const res = await fetch(`${API_URL}/admin/create-user`, { method: "POST", headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" }, body: JSON.stringify(data) });
    
    if(res.ok) {
        alert("User Created! Default Pass: Bvicam@2025");
        document.getElementById("adminName").value = "";
        document.getElementById("adminEmail").value = "";
        loadAdminUsers();
    } else { alert("Failed to create user"); }
}

async function deleteUser(id) {
    if(!confirm("Are you sure?")) return;
    const res = await fetch(`${API_URL}/admin/delete-user/${id}`, { method: "DELETE", headers: { "Authorization": `Bearer ${token}` } });
    if(res.ok) loadAdminUsers();
}