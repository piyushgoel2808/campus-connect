/**
 * Campus People Directory search, filtering, and profile preview modal controller (`components/directory.html`).
 */

import { get, getCurrentUser } from '../utils/api.js';
import { escapeHtml, safeHttpUrl } from '../utils/sanitize.js';

let searchTimeout = null;
const directoryUsersMap = new Map();

export function initDirectory() {
    runSearch();
}

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
    const visibleUsers = (users || []).filter(u => !myEmail || (u.email || "").toLowerCase() !== myEmail.toLowerCase());

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

        const deptRaw = u.departmentName ? `${u.departmentCode || u.departmentName} • ${u.role || ''}` : (u.role || 'Member');
        const batchDisplay = u.batchYear ? `Batch ${u.batchYear}` : 'Batch N/A';
        const companyInfo = u.currentCompany
            ? `<div class="small text-primary fw-semibold">${u.designation ? escapeHtml(u.designation) + ' @ ' : ''}${escapeHtml(u.currentCompany)}</div>`
            : '';
        const skillsDisplay = u.skills ? u.skills.split(',').slice(0, 3).map(s => s.trim()).filter(Boolean) : [];

        const tr = document.createElement("tr");
        tr.className = "cc-directory-row";
        tr.setAttribute("role", "button");
        tr.innerHTML = `
            <td>
                <div class="d-flex align-items-center gap-3">
                    <div class="avatar-circle">${escapeHtml((u.name || 'U').charAt(0).toUpperCase())}</div>
                    <div>
                        <div class="fw-bold">${escapeHtml(u.name || 'Member')}</div>
                        <div class="small text-muted">${escapeHtml(u.email || '')}</div>
                        ${companyInfo}
                    </div>
                </div>
            </td>
            <td>
                <div class="cc-stack gap-2">
                    <span class="cc-pill">${escapeHtml(deptRaw)}</span>
                    <span class="small text-muted">${escapeHtml(batchDisplay)}</span>
                </div>
            </td>
            <td>
                <div class="cc-stack gap-2">
                    <div class="d-flex flex-wrap gap-1">
                        ${skillsDisplay.map(skill => `<span class="badge rounded-pill text-bg-light border">${escapeHtml(skill)}</span>`).join('') || '<span class="text-muted small">No skills listed</span>'}
                    </div>
                    <button type="button" class="btn btn-sm btn-outline-primary align-self-start dir-msg-btn">
                        Message
                    </button>
                </div>
            </td>`;

        tr.addEventListener("click", () => window.openUserProfile(u.id));
        const msgBtn = tr.querySelector(".dir-msg-btn");
        if (msgBtn) {
            msgBtn.addEventListener("click", (event) => {
                event.stopPropagation();
                if (window.startDirectChat) {
                    window.startDirectChat(u.id, u.name || 'Member', u.email);
                }
            });
        }
        tbody.appendChild(tr);
    });
}

window.openUserProfile = function(userOrId) {
    const user = (typeof userOrId === 'object' && userOrId !== null)
        ? userOrId
        : directoryUsersMap.get(Number(userOrId));
    if (!user) return;

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

    const linkedinBtn = document.getElementById("linkedinBtn");
    const githubBtn = document.getElementById("githubBtn");
    const safeLinkedin = safeHttpUrl(user.linkedinUrl);
    const safeGithub = safeHttpUrl(user.githubUrl);

    if (linkedinBtn) {
        if (safeLinkedin) {
            linkedinBtn.href = safeLinkedin;
            linkedinBtn.classList.remove("d-none");
        } else {
            linkedinBtn.href = "#";
            linkedinBtn.classList.add("d-none");
        }
    }

    if (githubBtn) {
        if (safeGithub) {
            githubBtn.href = safeGithub;
            githubBtn.classList.remove("d-none");
        } else {
            githubBtn.href = "#";
            githubBtn.classList.add("d-none");
        }
    }

    document.getElementById("modalUserId").dataset.userName = user.name || "";
    document.getElementById("modalUserId").dataset.userEmail = user.email || "";

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

    if (window.startDirectChat) {
        window.startDirectChat(userId, userName, userEmail);
    }
};
