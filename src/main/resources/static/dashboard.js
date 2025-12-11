// --- CONFIGURATION ---
const API_URL = "http://localhost:8080/api";
const WS_URL = "http://localhost:8080/ws";

// --- STATE MANAGEMENT ---
const token = localStorage.getItem("jwt_token");
const userName = localStorage.getItem("user_name");
const userRole = localStorage.getItem("user_role"); // "STUDENT", "ALUMNI", "ADMIN"
let stompClient = null;

// --- INITIALIZATION ---
// 1. Check Auth
if (!token) {
    window.location.href = "index.html"; // Redirect to login if no token
} else {
    // 2. Setup UI
    document.getElementById("welcomeUser").innerText = `Hello, ${userName}`;
    document.getElementById("userRoleBadge").innerText = userRole;

    // Show "Post Job" button only for eligible roles
    if (userRole === "ALUMNI" || userRole === "ADMIN") {
        document.getElementById("btnPostJob").classList.remove("d-none");
    }

    // 3. Load Initial Data
    fetchUsers();    // Load Directory
    connectToChat(); // Connect WebSocket
}

// --- NAVIGATION LOGIC ---
function switchTab(tabName) {
    // Hide all views
    document.getElementById("view-directory").classList.add("d-none");
    document.getElementById("view-jobs").classList.add("d-none");
    document.getElementById("view-wall").classList.add("d-none");

    // Deactivate tabs
    document.querySelectorAll(".nav-link").forEach(btn => btn.classList.remove("active"));

    // Show selected view & activate tab
    document.getElementById(`view-${tabName}`).classList.remove("d-none");
    document.getElementById(`tab-${tabName}`).classList.add("active");

    // Lazy Load Data
    if (tabName === 'jobs') fetchJobs();
    if (tabName === 'wall') fetchPosts();
}

function logout() {
    localStorage.clear();
    window.location.href = "index.html";
}

// ============================================================
// MODULE 1: DIRECTORY (USERS)
// ============================================================
async function fetchUsers() {
    try {
        const response = await fetch(`${API_URL}/users`, {
            headers: { "Authorization": `Bearer ${token}` }
        });
        
        if (response.ok) {
            const users = await response.json();
            const tbody = document.getElementById("tableBody");
            tbody.innerHTML = "";
            
            users.forEach(user => {
                const roleBadge = user.role === 'ALUMNI' 
                    ? '<span class="badge bg-success">Alumni</span>' 
                    : '<span class="badge bg-primary">Student</span>';
                    
                tbody.innerHTML += `
                    <tr>
                        <td class="ps-4 fw-bold">${user.name}</td>
                        <td>${roleBadge}</td>
                        <td class="text-muted">${user.email}</td>
                        <td><span class="badge rounded-pill bg-light text-dark border">Active</span></td>
                    </tr>`;
            });
        }
    } catch (e) { console.error("Directory Error:", e); }
}

// ============================================================
// MODULE 2: JOB BOARD
// ============================================================
async function fetchJobs() {
    try {
        const response = await fetch(`${API_URL}/jobs`, {
            headers: { "Authorization": `Bearer ${token}` }
        });

        if (response.ok) {
            const jobs = await response.json();
            const list = document.getElementById("jobList");
            list.innerHTML = "";

            if (jobs.length === 0) {
                list.innerHTML = `<div class="text-center text-muted py-5">No active job postings.</div>`;
                return;
            }

            jobs.forEach(job => {
                list.innerHTML += `
                    <div class="card job-card shadow-sm mb-3">
                        <div class="card-body">
                            <div class="d-flex justify-content-between align-items-start">
                                <div>
                                    <h5 class="card-title mb-1">${job.title}</h5>
                                    <h6 class="card-subtitle text-muted mb-2">${job.company} • ${job.location}</h6>
                                </div>
                                <span class="badge bg-light text-dark border">${new Date(job.postedAt).toLocaleDateString()}</span>
                            </div>
                            <p class="card-text mt-2 text-secondary">${job.description}</p>
                            <a href="mailto:${job.applyLink}" class="btn btn-sm btn-outline-primary">Apply Now</a>
                        </div>
                    </div>`;
            });
        }
    } catch (e) { console.error("Jobs Error:", e); }
}

async function postJob() {
    const jobData = {
        title: document.getElementById("jobTitle").value,
        company: document.getElementById("jobCompany").value,
        location: document.getElementById("jobLocation").value,
        description: document.getElementById("jobDesc").value,
        applyLink: document.getElementById("jobLink").value
    };

    const response = await fetch(`${API_URL}/jobs`, {
        method: "POST",
        headers: { 
            "Authorization": `Bearer ${token}`,
            "Content-Type": "application/json"
        },
        body: JSON.stringify(jobData)
    });

    if (response.ok) {
        alert("Job Posted Successfully!");
        bootstrap.Modal.getInstance(document.getElementById('postJobModal')).hide();
        document.getElementById("jobForm").reset();
        fetchJobs(); // Refresh List
    } else {
        alert("Failed to post job.");
    }
}

// ============================================================
// MODULE 3: MEMORY WALL (SOCIAL FEED)
// ============================================================
async function fetchPosts() {
    try {
        const response = await fetch(`${API_URL}/posts`, {
            headers: { "Authorization": `Bearer ${token}` }
        });

        if (response.ok) {
            const posts = await response.json();
            const feed = document.getElementById("feedArea");
            feed.innerHTML = "";

            if (posts.length === 0) {
                feed.innerHTML = `<div class="text-center text-muted py-5">No posts yet. Be the first!</div>`;
                return;
            }

            posts.forEach(post => {
                const initial = post.author.name.charAt(0).toUpperCase();
                const imageHtml = post.imageUrl ? `<img src="${post.imageUrl}" alt="Post Image">` : '';

                feed.innerHTML += `
                    <div class="card post-card shadow-sm mb-3">
                        <div class="card-body">
                            <div class="d-flex align-items-center mb-3">
                                <div class="author-avatar">${initial}</div>
                                <div>
                                    <h6 class="mb-0 fw-bold">${post.author.name}</h6>
                                    <small class="text-muted" style="font-size: 0.8rem;">
                                        ${post.author.role} • ${new Date(post.createdAt).toLocaleDateString()}
                                    </small>
                                </div>
                            </div>
                            <p class="card-text">${post.content}</p>
                            ${imageHtml}
                        </div>
                    </div>`;
            });
        }
    } catch (e) { console.error("Wall Error:", e); }
}

async function createPost() {
    const content = document.getElementById("postContent").value;
    const imageUrl = document.getElementById("postImage").value;

    if (!content.trim()) return alert("Please write something!");

    const response = await fetch(`${API_URL}/posts`, {
        method: "POST",
        headers: { 
            "Authorization": `Bearer ${token}`,
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ content: content, imageUrl: imageUrl })
    });

    if (response.ok) {
        document.getElementById("postContent").value = "";
        document.getElementById("postImage").value = "";
        fetchPosts(); // Refresh Feed
    } else {
        alert("Failed to create post.");
    }
}

// ============================================================
// MODULE 4: REAL-TIME CHAT (WEBSOCKET)
// ============================================================
function connectToChat() {
    const socket = new SockJS(WS_URL);
    stompClient = Stomp.over(socket);
    stompClient.debug = null; // Disable debug logs

    stompClient.connect({}, function () {
        document.getElementById("chatArea").innerHTML += 
            `<div class="system-message text-success">🟢 Connected to Global Chat</div>`;
        
        // Subscribe to Public Topic
        stompClient.subscribe('/topic/public', function (payload) {
            const message = JSON.parse(payload.body);
            displayMessage(message);
        });

        // Announce Join
        stompClient.send("/app/chat.addUser", {}, JSON.stringify({ senderName: userName, type: 'JOIN' }));

    }, function(err) {
        console.error("Chat Error:", err);
        document.getElementById("chatArea").innerHTML += 
            `<div class="system-message text-danger">🔴 Connection Lost. Refreshing...</div>`;
    });
}

function sendMessage() {
    const input = document.getElementById("messageInput");
    const content = input.value.trim();

    if (content && stompClient) {
        const chatMessage = {
            senderName: userName,
            content: content,
            type: 'CHAT'
        };
        stompClient.send("/app/chat.sendMessage", {}, JSON.stringify(chatMessage));
        input.value = '';
    }
}

function displayMessage(message) {
    const chatArea = document.getElementById("chatArea");
    const msgDiv = document.createElement('div');

    if (message.type === 'JOIN') {
        msgDiv.className = 'system-message';
        msgDiv.innerText = `${message.senderName} joined the chat`;
    } else if (message.type === 'LEAVE') {
        msgDiv.className = 'system-message';
        msgDiv.innerText = `${message.senderName} left the chat`;
    } else {
        msgDiv.classList.add('message');
        if (message.senderName === userName) {
            msgDiv.classList.add('my-message');
            msgDiv.innerText = message.content; // No "You:" prefix for cleaner look
        } else {
            msgDiv.classList.add('other-message');
            msgDiv.innerHTML = `<strong>${message.senderName}</strong><br>${message.content}`;
        }
    }

    chatArea.appendChild(msgDiv);
    chatArea.scrollTop = chatArea.scrollHeight; // Auto-scroll
}

function handleEnter(e) {
    if (e.key === 'Enter') sendMessage();
}