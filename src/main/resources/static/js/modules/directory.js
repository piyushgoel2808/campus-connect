import { get, getCurrentUser } from '../utils/api.js';

let searchTimeout = null;
const directoryUsersMap = new Map();

export function initDirectory() {
    runSearch();
}

// --- Exposed Functions ---
window.debouncedSearch = function() {
    clearTimeout(searchTimeout);
    searchTimeout = setTimeout(window.runSearch, 300);
};

export async function runSearch() {
    const searchEl = document.getElementById("dirSearch");
    const roleEl = document.getElementById("filterRole");
    const batchEl = document.getElementById("filterBatch");
    const tbody = document.getElementById("tableBody");

    const q = searchEl ? searchEl.value.trim() : "";
    const role = roleEl ? roleEl.value : "ALL";
    const batch = batchEl ? batchEl.value : "";

    const params = new URLSearchParams();
    if (role && role !== "ALL") params.set("role", role);
    if (batch) params.set("batch", batch);
    if (q) params.set("q", q);

    try {
        const queryString = params.toString();
        const endpoint = queryString ? `/users/search?${queryString}` : `/users/search`;
        const users = await get(endpoint);
        renderDirectoryTable(Array.isArray(users) ? users : []);
    } catch (e) {
        console.error("Directory search failed:", e);
        if (tbody) {
            tbody.innerHTML = `<tr><td colspan="3" class="text-danger text-center py-4">Failed to search directory. Please try again.</td></tr>`;
        }
    }
}

window.runSearch = runSearch;

function renderDirectoryTable(users) {
    const tbody = document.getElementById("tableBody");
    if (!tbody) return;
    tbody.innerHTML = "";
    directoryUsersMap.clear();

    const { email: myEmail } = getCurrentUser();
    const visibleUsers = (users || []).filter(u => !myEmail || u.email.toLowerCase() !== myEmail.toLowerCase());

    if (visibleUsers.length === 0) {
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

    visibleUsers.forEach(u => {
        directoryUsersMap.set(u.id, u);

        const deptDisplay = u.departmentName ? `${u.departmentCode || u.departmentName} • ${u.role || ''}` : (u.role || 'Member');
        const batchDisplay = u.batchYear ? `Batch ${u.batchYear}` : 'Batch N/A';
        const companyInfo = u.currentCompany ? `<div class="small text-primary fw-semibold">${u.designation ? u.designation + ' @ ' : ''}${u.currentCompany}</div>` : '';
        const skillsDisplay = u.skills ? u.skills.split(',').slice(0, 3).map(s => s.trim()).filter(Boolean) : [];
        const safeName = (u.name || 'User').replace(/'/g, "\\'");

        tbody.innerHTML += `
            <tr class="cc-directory-row" onclick="window.openUserProfile(${u.id})" role="button">
                <td>
                    <div class="d-flex align-items-center gap-3">
                        <div class="avatar-circle">${(u.name || 'U').charAt(0).toUpperCase()}</div>
                        <div>
                            <div class="fw-bold">${u.name}</div>
                            <div class="small text-muted">${u.email}</div>
                            ${companyInfo}
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
                            onclick="event.stopPropagation(); window.startDirectChat('${u.id}', '${safeName}', '${u.email}')">
                            Message
                        </button>
                    </div>
                </td>
            </tr>`;
    });
}

window.openUserProfile = function(userOrId) {
    const user = (typeof userOrId === 'object' && userOrId !== null)
        ? userOrId
        : directoryUsersMap.get(Number(userOrId));
    if (!user) return;

    // Populate all profile fields in the modal
    document.getElementById("modalUserId").value = user.id || "";
    document.getElementById("modalName").innerText = user.name || "N/A";
    document.getElementById("modalEmail").innerText = user.email || "N/A";
    document.getElementById("modalRole").innerText = user.role || "N/A";
    document.getElementById("modalDept").innerText = user.departmentName || user.departmentCode || "N/A";
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
    
    const modalEl = document.getElementById('userProfileModal');
    if (modalEl) {
        bootstrap.Modal.getOrCreateInstance(modalEl).show();
    }
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