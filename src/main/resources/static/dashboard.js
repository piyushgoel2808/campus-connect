const API_URL = "/api";
const WS_URL = "/ws";
const token = localStorage.getItem("jwt_token");
const myEmail = localStorage.getItem("user_email");
const userName = localStorage.getItem("user_name");
const userRole = localStorage.getItem("user_role");

let stompClient = null;
let currentChatPartner = null;
let searchTimeout = null;

// --- INIT ---
if (!token) window.location.href = "index.html";
else {
    document.getElementById("welcomeUser").innerText = userName;
    if (userRole === "ALUMNI" || userRole === "ADMIN") document.getElementById("btnPostJob").classList.remove("d-none");
    
    // Ensure email is stored
    if (!myEmail) {
        fetch(`${API_URL}/users/me`, { headers: { "Authorization": `Bearer ${token}` } })
            .then(res => res.json())
            .then(user => localStorage.setItem("user_email", user.email));
    }

    // Default to Messages
    fetchRecentChats(); 
    connectToChat();
}

function switchTab(tab) {
    ['messages', 'directory', 'jobs', 'wall', 'profile'].forEach(t => {
        document.getElementById(`view-${t}`).classList.add('d-none');
        const btn = document.getElementById(`tab-${t}`);
        if(btn) btn.classList.remove('active');
    });

    document.getElementById(`view-${tab}`).classList.remove('d-none');
    const btn = document.getElementById(`tab-${tab}`);
    if(btn) btn.classList.add('active');

    if(tab === 'messages') fetchRecentChats();
    if(tab === 'directory') runSearch();
    if(tab === 'jobs') fetchJobs();
    if(tab === 'wall') fetchPosts();
    if(tab === 'profile') loadProfile();
}

function logout() { localStorage.clear(); window.location.href="index.html"; }

// ================= MODULE 1: PROFILE (RESTORED!) =================
async function loadProfile() {
    try {
        const res = await fetch(`${API_URL}/users/me`, { headers: { "Authorization": `Bearer ${token}` } });
        if(res.ok) {
            const user = await res.json();
            document.getElementById("pHeadline").value = user.headline || "";
            document.getElementById("pCompany").value = user.currentCompany || "";
            document.getElementById("pDesignation").value = user.designation || "";
            document.getElementById("pSkills").value = user.skills || "";
            document.getElementById("pExperience").value = user.pastExperience || "";
            document.getElementById("pLinkedin").value = user.linkedinUrl || "";
            document.getElementById("pGithub").value = user.githubUrl || "";
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

// ================= MODULE 2: CAMPUS BOT (RESTORED!) =================
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

// ================= MODULE 3: MESSAGES & CHAT =================
async function fetchRecentChats() {
    const res = await fetch(`${API_URL}/messages/partners`, { headers: { "Authorization": `Bearer ${token}` } });
    if(res.ok) {
        const users = await res.json();
        const list = document.getElementById("recentChatsList");
        list.innerHTML = "";
        if(users.length === 0) list.innerHTML = `<div class="text-center py-5 text-muted">No chats.</div>`;
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

function connectToChat() {
    const socket = new SockJS(WS_URL);
    stompClient = Stomp.over(socket);
    stompClient.debug = null;

    stompClient.connect({}, function () {
        document.getElementById("chatStatus").innerText = "Online";
        stompClient.subscribe('/topic/public', payload => displayGlobalMsg(JSON.parse(payload.body)));
        stompClient.subscribe('/user/queue/messages', payload => {
            const msg = JSON.parse(payload.body);
            fetchRecentChats(); // Auto-refresh list
            if (currentChatPartner && (msg.senderName === currentChatPartner || msg.senderName === myEmail)) {
                displayPrivateMsg(msg);
            } else if (msg.senderName !== myEmail) {
                document.getElementById("msgBadge").classList.remove("d-none");
                const toastBody = document.getElementById("toastBody");
                toastBody.innerText = `${msg.senderName}: ${msg.content}`;
                new bootstrap.Toast(document.getElementById('liveToast')).show();
            }
        });
    });
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
    const isMe = msg.senderName === myEmail; // Now works correctly
    area.innerHTML += `<div class="message ${isMe?'my-message':'other-message'}">${msg.content}</div>`;
    area.scrollTop = area.scrollHeight;
}

// ================= MODULE 4: DIRECTORY (ADVANCED) =================
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
                    <tr class="user-row" onclick='openChatWithUser(${JSON.stringify(u)})'>
                        <td><div class="avatar-circle small">${initial}</div></td>
                        <td><div class="fw-bold text-dark">${u.name}</div><small class="text-muted">${u.headline||u.role}</small></td>
                        <td><span class="badge ${u.role==='ALUMNI'?'bg-success':'bg-primary'}">${u.role}</span></td>
                        <td><button class="btn btn-sm btn-outline-primary"><i class="fas fa-paper-plane"></i></button></td>
                    </tr>`;
            });
        }
    } catch(e) { console.error(e); }
}

// ================= GLOBAL CHAT & JOBS & WALL =================
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