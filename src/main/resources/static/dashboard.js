// --- CONFIGURATION ---
const API_URL = "/api";  // Relative path for Mobile support
const WS_URL = "/ws";

// --- STATE ---
const token = localStorage.getItem("jwt_token");
const userName = localStorage.getItem("user_name");
const userRole = localStorage.getItem("user_role");
let stompClient = null;

// --- INITIALIZATION ---
if (!token) {
    window.location.href = "index.html";
} else {
    // 1. Setup Header Info
    document.getElementById("welcomeUser").innerText = `Hello, ${userName}`;
    document.getElementById("userRoleBadge").innerText = userRole;

    // 2. Show Admin/Alumni Buttons
    if (userRole === "ALUMNI" || userRole === "ADMIN") {
        document.getElementById("btnPostJob").classList.remove("d-none");
    }

    // 3. Initial Load
    fetchUsers();
    connectToChat();
    checkPasswordStatus(); // NEW: Check password on startup
}

// --- NAVIGATION ---
function switchTab(tabName) {
    // Hide all views
    document.getElementById("view-directory").classList.add("d-none");
    document.getElementById("view-jobs").classList.add("d-none");
    document.getElementById("view-wall").classList.add("d-none");
    document.getElementById("view-profile").classList.add("d-none");
    document.getElementById("view-admin").classList.add("d-none"); // NEW: Hide Admin

    // Show selected
    document.getElementById(`view-${tabName}`).classList.remove("d-none");

    // Lazy Load Data
    if (tabName === 'jobs') fetchJobs();
    if (tabName === 'wall') fetchPosts();
    if (tabName === 'profile') loadProfile();
    // NEW: Load Admin Data
    if (tabName === 'admin') {
        loadAdminUsers();
    }
}

function logout() {
    localStorage.clear();
    window.location.href = "index.html";
}

// ================= MODULE 1: DIRECTORY =================
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
                const roleColor = user.role === 'ALUMNI' ? 'bg-success' : 'bg-primary';
                const headline = user.headline ? `<small class="text-muted d-block">${user.headline}</small>` : '';
                
                tbody.innerHTML += `
                    <tr>
                        <td>
                            <div class="fw-bold text-dark">${user.name}</div>
                            ${headline}
                        </td>
                        <td><span class="badge ${roleColor}">${user.role}</span></td>
                        <td class="text-muted small">${user.email}</td>
                    </tr>`;
            });
        }
    } catch (e) { console.error(e); }
}

// ================= MODULE 2: JOB BOARD =================
async function fetchJobs() {
    try {
        const response = await fetch(`${API_URL}/jobs`, {
            headers: { "Authorization": `Bearer ${token}` }
        });
        if (response.ok) {
            const jobs = await response.json();
            const list = document.getElementById("jobList");
            list.innerHTML = "";
            if(jobs.length === 0) list.innerHTML = `<div class="text-center text-muted py-5">No jobs active.</div>`;
            
            jobs.forEach(job => {
                list.innerHTML += `
                    <div class="content-card job-card p-3">
                        <div class="d-flex justify-content-between">
                            <h5 class="mb-1 text-primary">${job.title}</h5>
                            <small class="text-muted">${new Date(job.postedAt).toLocaleDateString()}</small>
                        </div>
                        <h6 class="text-dark mb-2">${job.company} &bull; <span class="text-muted small">${job.location}</span></h6>
                        <p class="text-secondary small">${job.description}</p>
                        <a href="mailto:${job.applyLink}" class="btn btn-sm btn-outline-primary px-3">Apply Now</a>
                    </div>`;
            });
        }
    } catch (e) { console.error(e); }
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
        headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify(jobData)
    });

    if (response.ok) {
        bootstrap.Modal.getInstance(document.getElementById('postJobModal')).hide();
        fetchJobs();
    } else { alert("Failed to post job."); }
}

// ================= MODULE 3: MEMORY WALL =================
async function fetchPosts() {
    try {
        const response = await fetch(`${API_URL}/posts`, {
            headers: { "Authorization": `Bearer ${token}` }
        });
        if (response.ok) {
            const posts = await response.json();
            const feed = document.getElementById("feedArea");
            feed.innerHTML = "";
            if(posts.length === 0) feed.innerHTML = `<div class="text-center text-muted py-5">No posts yet.</div>`;

            posts.forEach(post => {
                const initial = post.author.name.charAt(0).toUpperCase();
                const imageHtml = post.imageUrl ? `<img src="${post.imageUrl}" class="img-fluid rounded mt-2" style="max-height:300px; width:100%; object-fit:cover;">` : '';
                
                feed.innerHTML += `
                    <div class="content-card p-3">
                        <div class="d-flex align-items-center mb-2">
                            <div class="bg-light rounded-circle d-flex align-items-center justify-content-center me-2 fw-bold text-secondary" style="width:35px; height:35px;">${initial}</div>
                            <div>
                                <div class="fw-bold text-dark" style="font-size:0.9rem;">${post.author.name}</div>
                                <div class="text-muted small" style="font-size:0.75rem;">${post.author.role} &bull; ${new Date(post.createdAt).toLocaleDateString()}</div>
                            </div>
                        </div>
                        <p class="mb-1 text-dark">${post.content}</p>
                        ${imageHtml}
                    </div>`;
            });
        }
    } catch (e) { console.error(e); }
}

async function createPost() {
    const content = document.getElementById("postContent").value;
    const imageUrl = document.getElementById("postImage").value;
    if(!content.trim()) return;

    const response = await fetch(`${API_URL}/posts`, {
        method: "POST",
        headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" },
        body: JSON.stringify({ content: content, imageUrl: imageUrl })
    });

    if (response.ok) {
        document.getElementById("postContent").value = "";
        document.getElementById("postImage").value = "";
        fetchPosts();
    }
}

// ================= MODULE 4: PROFILE =================
async function loadProfile() {
    try {
        const response = await fetch(`${API_URL}/users/me`, {
            headers: { "Authorization": `Bearer ${token}` }
        });
        if (response.ok) {
            const user = await response.json();
            document.getElementById("pHeadline").value = user.headline || "";
            document.getElementById("pCompany").value = user.currentCompany || "";
            document.getElementById("pDesignation").value = user.designation || "";
            document.getElementById("pSkills").value = user.skills || "";
            document.getElementById("pExperience").value = user.pastExperience || "";
            document.getElementById("pLinkedin").value = user.linkedinUrl || "";
            document.getElementById("pGithub").value = user.githubUrl || "";
        }
    } catch (e) { console.error(e); }
}

async function saveProfile() {
    const headline = document.getElementById("pHeadline").value;
    const company = document.getElementById("pCompany").value;
    const designation = document.getElementById("pDesignation").value;
    const skills = document.getElementById("pSkills").value;
    const experience = document.getElementById("pExperience").value;
    const linkedin = document.getElementById("pLinkedin").value;
    const github = document.getElementById("pGithub").value;

    const profileData = {
        headline: headline,
        currentCompany: company,
        designation: designation,
        skills: skills,
        pastExperience: experience,
        linkedinUrl: linkedin,
        githubUrl: github
    };

    try {
        const response = await fetch(`${API_URL}/users/profile`, {
            method: "PUT",
            headers: { 
                "Authorization": `Bearer ${token}`,
                "Content-Type": "application/json"
            },
            body: JSON.stringify(profileData)
        });

        if (response.ok) {
            alert("✅ Profile Updated Successfully!");
            fetchUsers();
        } else {
            const errorText = await response.text();
            alert("❌ Failed to update: " + errorText);
        }
    } catch (error) {
        alert("⚠️ Network Error: " + error.message);
    }
}

// ================= MODULE 5: CHAT =================
function connectToChat() {
    const socket = new SockJS(WS_URL);
    stompClient = Stomp.over(socket);
    stompClient.debug = null; 

    stompClient.connect({}, function () {
        document.getElementById("chatStatus").className = "badge bg-success rounded-pill";
        document.getElementById("chatStatus").innerText = "Online";
        document.getElementById("chatArea").innerHTML = ""; // Clear connecting msg
        
        stompClient.subscribe('/topic/public', function (payload) {
            displayMessage(JSON.parse(payload.body));
        });
        stompClient.send("/app/chat.addUser", {}, JSON.stringify({ senderName: userName, type: 'JOIN' }));
    }, function() {
        document.getElementById("chatStatus").className = "badge bg-danger rounded-pill";
        document.getElementById("chatStatus").innerText = "Offline";
    });
}

function sendMessage() {
    const input = document.getElementById("messageInput");
    if (input.value.trim() && stompClient) {
        stompClient.send("/app/chat.sendMessage", {}, JSON.stringify({ senderName: userName, content: input.value, type: 'CHAT' }));
        input.value = '';
    }
}

function displayMessage(message) {
    const chatArea = document.getElementById("chatArea");
    const msgDiv = document.createElement('div');

    if (message.type === 'CHAT') {
        msgDiv.className = message.senderName === userName ? 'message my-message' : 'message other-message';
        const senderLabel = message.senderName !== userName ? `<div style="font-size:0.7rem; font-weight:bold; color:#666; margin-bottom:2px;">${message.senderName}</div>` : '';
        msgDiv.innerHTML = `${senderLabel}${message.content}`;
    } else {
        msgDiv.className = 'system-message';
        msgDiv.innerText = `${message.senderName} ${message.type === 'JOIN' ? 'joined' : 'left'}`;
    }
    
    chatArea.appendChild(msgDiv);
    chatArea.scrollTop = chatArea.scrollHeight;
}

function handleEnter(e) { if (e.key === 'Enter') sendMessage(); }


// ================= MODULE 6: ADMIN & SECURITY TOOLS =================

async function checkPasswordStatus() {
    try {
        const response = await fetch(`${API_URL}/users/me`, {
            headers: { "Authorization": `Bearer ${token}` }
        });
        if (response.ok) {
            const user = await response.json();

            // 1. Show Admin Tab if user is Admin
            if (user.role === 'ADMIN') {
                document.getElementById('tab-admin').classList.remove('d-none');
            }

            // 2. Check if password needs change
            if (user.passwordChanged === false) {
                new bootstrap.Modal(document.getElementById('passwordModal')).show();
            }
        }
    } catch(e) { console.error("Error checking status", e); }
}

async function adminCreateUser() {
    const userData = {
        name: document.getElementById("adminName").value,
        email: document.getElementById("adminEmail").value,
        role: document.getElementById("adminRole").value,
        password: "placeholder" // Backend ignores this and sets default
    };

    try {
        const response = await fetch(`${API_URL}/admin/create-user`, {
            method: "POST",
            headers: { "Authorization": `Bearer ${token}`, "Content-Type": "application/json" },
            body: JSON.stringify(userData)
        });

        if (response.ok) {
            alert("User Created! Default Password: Bvicam@2025");
            document.getElementById("adminName").value = "";
            document.getElementById("adminEmail").value = "";
            loadAdminUsers(); // Refresh list
        } else {
            alert("Failed to create user.");
        }
    } catch(e) { console.error(e); }
}

async function loadAdminUsers() {
    const response = await fetch(`${API_URL}/users`, { headers: { "Authorization": `Bearer ${token}` } });
    const users = await response.json();
    const tbody = document.getElementById("adminUserTable");
    tbody.innerHTML = "";

    users.forEach(user => {
        tbody.innerHTML += `
            <tr>
                <td>${user.id}</td>
                <td>${user.name}</td>
                <td><span class="badge bg-secondary">${user.role}</span></td>
                <td>${user.email}</td>
                <td>
                    <button class="btn btn-sm btn-outline-danger" onclick="deleteUser(${user.id})">
                        <i class="fas fa-trash"></i>
                    </button>
                </td>
            </tr>`;
    });
}

async function deleteUser(id) {
    if(!confirm("Are you sure?")) return;
    const response = await fetch(`${API_URL}/admin/delete-user/${id}`, {
        method: "DELETE",
        headers: { "Authorization": `Bearer ${token}` }
    });
    if(response.ok) loadAdminUsers();
}

async function changePassword() {
    const newPass = document.getElementById("newPassword").value;
    if(newPass.length < 4) return alert("Password too short!");

    // Fetch 'me' to get email
    const meResponse = await fetch(`${API_URL}/users/me`, { headers: { "Authorization": `Bearer ${token}` } });
    const meParams = await meResponse.json();

    const response = await fetch(`${API_URL}/auth/change-password`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: meParams.email, newPassword: newPass })
    });

    if (response.ok) {
        alert("Password Changed! Please Login again.");
        logout();
    }
}