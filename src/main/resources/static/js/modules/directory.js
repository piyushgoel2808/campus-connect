import { get, getCurrentUser } from '../utils/api.js';

let searchTimeout = null;

export function initDirectory() {
    // Attach event listeners manually if preferred, or use inline onclick
}

// --- Exposed Functions ---
window.debouncedSearch = function() {
    clearTimeout(searchTimeout);
    searchTimeout = setTimeout(window.runSearch, 400);
};

window.runSearch = async function() {
    const q = document.getElementById("dirSearch").value;
    const role = document.getElementById("filterRole").value;
    const batch = document.getElementById("filterBatch").value;

    const queryParams = new URLSearchParams({ role, batch, q }).toString();
    const users = await get(`/users/search?${queryParams}`);
    renderDirectoryTable(users);
};

function renderDirectoryTable(users) {
    const tbody = document.getElementById("tableBody");
    tbody.innerHTML = "";
    const { email: myEmail } = getCurrentUser();

    if (!users || users.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="3">
                    <div class="cc-empty-state">
                        <i class="fas fa-search fa-lg"></i>
                        <h5 class="mb-2">No people found</h5>
                        <p class="mb-0">Try a different role, batch, or keyword.</p>
                    </div>
                </td>
            </tr>`;
        return;
    }

    users.forEach(u => {
        if (u.email === myEmail) return;

        const deptDisplay = u.departmentName ? u.departmentName : (u.role || 'No Role');
        const batchDisplay = u.batchYear ? `Batch ${u.batchYear}` : 'Batch N/A';
        const skillsDisplay = u.skills ? u.skills.split(',').slice(0, 3).map(s => s.trim()).filter(Boolean) : [];

        tbody.innerHTML += `
            <tr class="cc-directory-row" onclick='window.openUserProfile(${JSON.stringify(u).replace(/'/g, "&#39;")})' role="button">
                <td>
                    <div class="d-flex align-items-center gap-3">
                        <div class="avatar-circle">${(u.name || 'U').charAt(0).toUpperCase()}</div>
                        <div>
                            <div class="fw-bold">${u.name}</div>
                            <div class="small text-muted">${u.email}</div>
                        </div>
                    </div>
                </td>
                <td>
                    <div class="cc-stack gap-2">
                        <span class="cc-pill">${deptDisplay}</span>
                        <span class="small text-muted">${batchDisplay}</span>
                    </div>
                </td>
                <td>
                    <div class="cc-stack gap-2">
                        <div class="d-flex flex-wrap gap-1">
                            ${skillsDisplay.map(skill => `<span class="badge rounded-pill text-bg-light border">${skill}</span>`).join('') || '<span class="text-muted small">No skills listed</span>'}
                        </div>
                        <button class="btn btn-sm btn-outline-primary align-self-start"
                            onclick="event.stopPropagation(); window.startDirectChat('${u.id}', '${u.name}', '${u.email}')">
                            Message
                        </button>
                    </div>
                </td>
            </tr>`;
    });
}

window.openUserProfile = function(user) {
    // Populate all profile fields in the modal
    document.getElementById("modalUserId").value = user.id || "";
    document.getElementById("modalName").innerText = user.name || "N/A";
    document.getElementById("modalEmail").innerText = user.email || "N/A";
    document.getElementById("modalRole").innerText = user.role || "N/A";
    document.getElementById("modalDept").innerText = user.departmentName || "N/A";
    document.getElementById("modalHeadline").innerText = user.headline || "N/A";
    document.getElementById("modalCompany").innerText = user.currentCompany || "N/A";
    document.getElementById("modalDesignation").innerText = user.designation || "N/A";
    document.getElementById("modalSkills").innerText = user.skills || "N/A";
    document.getElementById("modalBatch").innerText = user.batchYear || "N/A";
    document.getElementById("modalEnrollment").innerText = user.enrollmentNumber || "N/A";
    document.getElementById("modalBio").innerText = user.pastExperience || "N/A";
    
    // Set LinkedIn and GitHub URLs if available
    const linkedinBtn = document.getElementById("linkedinBtn");
    const githubBtn = document.getElementById("githubBtn");
    
    if(user.linkedinUrl) {
        linkedinBtn.classList.remove("d-none");
        linkedinBtn.href = user.linkedinUrl;
    } else {
        linkedinBtn.classList.add("d-none");
    }
    
    if(user.githubUrl) {
        githubBtn.classList.remove("d-none");
        githubBtn.href = user.githubUrl;
    } else {
        githubBtn.classList.add("d-none");
    }

    // Store the user data in the modal for messaging
    document.getElementById("modalUserId").dataset.userName = user.name;
    document.getElementById("modalUserId").dataset.userEmail = user.email;
    
    new bootstrap.Modal(document.getElementById('userProfileModal')).show();
};

window.startDirectChatFromModal = function() {
    const userIdEl = document.getElementById("modalUserId");
    const userId = userIdEl.value;
    const userName = userIdEl.dataset.userName;
    const userEmail = userIdEl.dataset.userEmail;
    
    if(window.startDirectChat) {
        window.startDirectChat(userId, userName, userEmail);
    }
};