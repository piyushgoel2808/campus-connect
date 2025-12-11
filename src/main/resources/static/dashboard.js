// --- CONFIGURATION ---
const API_URL = "/api";
const WS_URL = "/ws";

// --- STATE ---
const token = localStorage.getItem("jwt_token");
const myEmail = localStorage.getItem("user_email"); // Make sure your Login saves this!
const userName = localStorage.getItem("user_name");
const userRole = localStorage.getItem("user_role");

let stompClient = null;
let currentChatPartner = null;
let searchTimeout = null; // For debounce

// --- INITIALIZATION ---
if (!token) {
    window.location.href = "index.html";
} else {
    // Setup Header
    document.getElementById("welcomeUser").innerText = userName;
    if (userRole === "ALUMNI" || userRole === "ADMIN") {
        const postBtn = document.getElementById("btnPostJob");
        if(postBtn) postBtn.classList.remove("d-none");
    }
    
    // Initial Loads
    fetchRecentChats(); 
    connectToChat();
}

// --- NAVIGATION ---
function switchTab(tab) {
    // Hide all views
    ['messages', 'directory', 'jobs', 'wall', 'profile'].forEach(t => {
        const view = document.getElementById(`view-${t}`);
        const btn = document.getElementById(`tab-${t}`);
        if(view) view.classList.add('d-none');
        if(btn) btn.classList.remove('active');
    });

    // Show selected view
    const view = document.getElementById(`view-${tab}`);
    const btn = document.getElementById(`tab-${tab}`);
    if(view) view.classList.remove('d-none');
    if(btn) btn.classList.add('active');

    // Tab Specific Logic
    if(tab === 'messages') fetchRecentChats();
    if(tab === 'directory') initDirectory(); // UPDATED: Calls new advanced logic
    if(tab === 'jobs') fetchJobs();
    if(tab === 'wall') fetchPosts();
    if(tab === 'profile') loadProfile();
}

// ==========================================
// MODULE 1: ADVANCED DIRECTORY
// ==========================================

function initDirectory() {
    runSearch(); // Load default view
}

// The Master Search Function
async function runSearch() {
    const q = document.getElementById("dirSearch").value;
    const role = document.getElementById("filterRole").value;
    const batch = document.getElementById("filterBatch").value;

    const tbody = document.getElementById("tableBody");
    tbody.innerHTML = `<tr><td colspan="5" class="text-center py-4"><div class="spinner-border text-primary"></div></td></tr>`;

    try {
        // Call the Backend API (Ensure your Backend Controller supports these params)
        const url = `${API_URL}/users/search?role=${role}&batch=${batch}&q=${encodeURIComponent(q)}`;
        const response = await fetch(url, { headers: { "Authorization": `Bearer ${token}` } });
        
        if (response.ok) {
            const users = await response.json();
            renderDirectoryTable(users);
        }
    } catch (e) { console.error("Search failed", e); }
}

// "Debounce": Waits 400ms after typing stops before calling API
function debouncedSearch() {
    clearTimeout(searchTimeout);
    searchTimeout = setTimeout(runSearch, 400); 
}

function renderDirectoryTable(users) {
    const tbody = document.getElementById("tableBody");
    tbody.innerHTML = "";

    if (users.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5" class="text-center py-5 text-muted">No users found matching filters.</td></tr>`;
        return;
    }

    users.forEach(user => {
        if (user.email === myEmail) return; // Hide self
        
        const initial = user.name.charAt(0).toUpperCase();
        // Handle null batchYear gracefully
        const batchBadge = user.batchYear ? `<span class="badge bg-light text-dark border">${user.batchYear}</span>` : '<span class="text-muted small">-</span>';
        
        tbody.innerHTML += `
            <tr class="user-row" onclick='openUserProfile(${JSON.stringify(user)})'>
                <td class="ps-3"><div class="avatar-circle small">${initial}</div></td>
                <td>
                    <div class="fw-bold text-dark">${user.name}</div>
                    <small class="text-muted">${user.headline || user.currentCompany || "Member"}</small>
                </td>
                <td>${batchBadge}</td>
                <td><span class="badge ${user.role==='ALUMNI'?'bg-success':'bg-primary'}">${user.role}</span></td>
                <td class="text-end pe-3">
                    <button class="btn btn-sm btn-outline-primary" onclick="event.stopPropagation(); openChatWithUser(${JSON.stringify(user)})">
                        <i class="fas fa-paper-plane"></i> Msg
                    </button>
                </td>
            </tr>`;
    });
}

// Placeholder for Profile Modal (Optional)
function openUserProfile(user) {
    alert(`View Profile: ${user.name}\n\nCompany: ${user.currentCompany || 'N/A'}\nHeadline: ${user.headline || 'N/A'}`);
}

// ==========================================
// MODULE 2: MESSAGING (WhatsApp Style)
// ==========================================

async function fetchRecentChats() {
    try {
        const res = await fetch(`${API_URL}/messages/partners`, { headers: { "Authorization": `Bearer ${token}` } });
        if(res.ok) {
            const users = await res.json();
            const list = document.getElementById("recentChatsList");
            if(list) {
                list.innerHTML = "";
                
                if(users.length === 0) {
                    list.innerHTML = `<div class="text-center py-5 text-muted">No conversations yet. Go to Directory to start chatting!</div>`;
                    return;
                }

                users.forEach(user => {
                    const initial = user.name.charAt(0).toUpperCase();
                    list.innerHTML += `
                        <div class="chat-item d-flex align-items-center" onclick='openChatWithUser(${JSON.stringify(user)})'>
                            <div class="avatar-circle me-3">${initial}</div>
                            <div class="flex-grow-1">
                                <div class="fw-bold text-dark">${user.name}</div>
                                <small class="text-muted">${user.headline || user.role}</small>
                            </div>
                            <div class="text-end">
                                <button class="btn btn-sm btn-light"><i class="fas fa-chevron-right"></i></button>
                            </div>
                        </div>`;
                });
            }
        }
    } catch(e) { console.error(e); }
}

function openChatWithUser(user) {
    currentChatPartner = user.email; // Using email as ID
    document.getElementById("privChatTitle").innerText = "Chat with " + user.name;
    const modalEl = document.getElementById('privateChatModal');
    if(modalEl) {
        const modal = new bootstrap.Modal(modalEl);
        modal.show();
    }
    loadChatHistory(currentChatPartner);
}

// ==========================================
// MODULE 3: WEBSOCKETS (UPDATED)
// ==========================================

function connectToChat() {
    const socket = new SockJS(WS_URL);
    stompClient = Stomp.over(socket);
    stompClient.debug = null;

    stompClient.connect({}, function () {
        const statusEl = document.getElementById("chatStatus");
        if(statusEl) {
            statusEl.innerText = "Online";
            statusEl.className = "badge bg-success ms-2";
        }
        
        // 1. Public Chat Subscription
        stompClient.subscribe('/topic/public', payload => displayGlobalMsg(JSON.parse(payload.body)));
        
        // 2. Private Message Subscription (UPDATED LOGIC)
        stompClient.subscribe('/user/queue/messages', payload => {
            const msg = JSON.parse(payload.body);
            
            // 🔴 CRITICAL FIX: Refresh Messages List Instantly
            fetchRecentChats(); 

            // If Chat Modal is Open with this person, append message
            if (currentChatPartner && (msg.senderName === currentChatPartner || msg.senderName === myEmail)) {
                displayPrivateMsg(msg);
            } 
            // Else show notification
            else if (msg.senderName !== myEmail) {
                showToastNotification(msg.senderName, msg.content);
                const badge = document.getElementById("msgBadge");
                if(badge) badge.classList.remove("d-none");
            }
        });
    }, function() { 
        const statusEl = document.getElementById("chatStatus");
        if(statusEl) {
            statusEl.innerText = "Offline";
            statusEl.className = "badge bg-danger ms-2";
        }
    });
}

function showToastNotification(sender, content) {
    const toastBody = document.getElementById("toastBody");
    if(toastBody) {
        toastBody.innerText = `${sender}: ${content}`;
        const toastEl = document.getElementById('liveToast');
        if(toastEl) new bootstrap.Toast(toastEl).show();
    }
}

// --- CHAT HELPERS ---

function sendMessage() {
    const input = document.getElementById("messageInput");
    if(input.value.trim()) {
        stompClient.send("/app/chat.sendMessage", {}, JSON.stringify({ senderName: userName, content: input.value, type: 'CHAT' }));
        input.value = "";
    }
}

function displayGlobalMsg(msg) {
    const area = document.getElementById("chatArea");
    if(area) {
        area.innerHTML += `<div class="message ${msg.senderName===userName?'my-message':'other-message'}"><b>${msg.senderName}:</b> ${msg.content}</div>`;
        area.scrollTop = area.scrollHeight;
    }
}

async function loadChatHistory(partner) {
    const res = await fetch(`${API_URL}/messages/history?partnerEmail=${partner}`, { headers: { "Authorization": `Bearer ${token}` } });
    if(res.ok) {
        const msgs = await res.json();
        const area = document.getElementById("privateChatArea");
        area.innerHTML = "";
        msgs.forEach(m => displayPrivateMsg({ senderName: m.senderEmail, content: m.content }));
    }
}

function sendPrivateMessage() {
    const input = document.getElementById("privMsgInput");
    const content = input.value.trim();
    if(!content) return;
    
    stompClient.send("/app/chat.private", {}, JSON.stringify({ senderName: myEmail, receiverName: currentChatPartner, content: content, type: 'CHAT' }));
    
    // Immediate UI Update (Optimistic update)
    displayPrivateMsg({ senderName: myEmail, content: content });
    input.value = "";
}

function displayPrivateMsg(msg) {
    const area = document.getElementById("privateChatArea");
    if(area) {
        const isMe = msg.senderName === myEmail;
        area.innerHTML += `<div class="message ${isMe?'my-message':'other-message'}">${msg.content}</div>`;
        area.scrollTop = area.scrollHeight;
    }
}

// ==========================================
// PLACEHOLDERS (Jobs, Wall, Profile)
// ==========================================
function fetchJobs() { console.log("Jobs placeholder"); }
function fetchPosts() { console.log("Posts placeholder"); }
function loadProfile() { console.log("Profile placeholder"); }
function createPost() {} 
function saveProfile() {}