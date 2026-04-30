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

    users.forEach(u => {
        if (u.email === myEmail) return;

        const deptDisplay = u.departmentName ? u.departmentName : (u.role || 'No Role');

        tbody.innerHTML += `
            <tr onclick='window.openUserProfile(${JSON.stringify(u).replace(/'/g, "&#39;")})'>
                <td><div class="fw-bold">${u.name}</div></td>
                <td><span class="badge bg-primary">${deptDisplay}</span></td>
                <td class="text-end">
                    <button class="btn btn-sm btn-outline-primary"
                        onclick="event.stopPropagation(); window.startDirectChat('${u.id}', '${u.name}', '${u.email}')">
                        Message
                    </button>
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