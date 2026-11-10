/**
 * Admin Control Panel module (`components/admin.html`).
 */

import { get, send, getCurrentUser, parseErrorMessage } from '../utils/api.js';
import { escapeHtml, safeHttpUrl } from '../utils/sanitize.js';

const adminUsersMap = new Map();

const setVal = (id, val) => {
    const el = document.getElementById(id);
    if (el) el.value = val ?? "";
};

const getVal = (id) => {
    const el = document.getElementById(id);
    return el ? el.value.trim() : "";
};

export function loadAdminModule() {
    const { role } = getCurrentUser();
    if (!role || role.toUpperCase() !== 'ADMIN') {
        console.warn("Unauthorized access to Admin module");
        return;
    }

    loadAdminUsers();
    window.loadAdminJobs();

    const feedbackBtn = document.getElementById('tab-feedback-btn');
    const usersBtn = document.getElementById('tab-users-btn');
    const jobsBtn = document.getElementById('tab-jobs-admin-btn');
    const knowledgeBtn = document.getElementById('tab-knowledge-btn');

    if (feedbackBtn) feedbackBtn.onclick = loadAdminFeedback;
    if (usersBtn) usersBtn.onclick = loadAdminUsers;
    if (jobsBtn) jobsBtn.onclick = window.loadAdminJobs;
    if (knowledgeBtn) knowledgeBtn.onclick = window.loadAdminKnowledge;
}

async function loadAdminUsers() {
    try {
        const users = await get('/users');
        const tbody = document.getElementById("adminUserTable");
        if (!tbody) return;

        adminUsersMap.clear();
        (users || []).forEach(u => adminUsersMap.set(u.id, u));

        tbody.innerHTML = (users || []).map(u => {
            const flagCount = Number(u.flagCount) || 1;
            const statusBadge = u.isFlagged
                ? `<span class="cc-flag-badge"><i class="fas fa-flag"></i> Flagged (${flagCount}x)</span>`
                : `<span class="badge bg-success bg-opacity-10 text-success border border-success border-opacity-25"><i class="fas fa-check-circle me-1"></i> Clean</span>`;

            const unflagBtn = u.isFlagged
                ? `<button class="btn btn-sm btn-outline-warning me-1" title="Clear Profanity Flags" onclick="window.unflagUser(${u.id})">
                     <i class="fas fa-flag-checkered me-1"></i> Unflag
                   </button>`
                : "";

            const deptText = u.departmentCode || u.departmentName || '';
            const batchText = u.batchYear ? `• Batch ${u.batchYear}` : '';

            return `
            <tr>
                <td>${Number(u.id) || 0}</td>
                <td>
                    <div class="fw-semibold">${escapeHtml(u.name || 'User')}</div>
                    <small class="text-muted">${escapeHtml(deptText)} ${escapeHtml(batchText)}</small>
                </td>
                <td><span class="badge bg-light text-dark border">${escapeHtml(u.role || '')}</span></td>
                <td>${escapeHtml(u.email || '')}</td>
                <td>${statusBadge}</td>
                <td class="text-end">
                    ${unflagBtn}
                    <button class="btn btn-sm btn-outline-primary me-1" title="Edit User"
                        onclick="window.openEditUserModal(${u.id})">
                        <i class="fas fa-edit"></i>
                    </button>
                    <button class="btn btn-sm btn-outline-danger" title="Delete User"
                        onclick="window.deleteUser(${u.id})">
                        <i class="fas fa-trash"></i>
                    </button>
                </td>
            </tr>`;
        }).join('');
    } catch (e) {
        console.error("Failed to load users", e);
        const tbody = document.getElementById("adminUserTable");
        if (tbody) tbody.innerHTML = `<tr><td colspan="6" class="text-danger text-center">Error loading users</td></tr>`;
    }
}

window.unflagUser = async function(id) {
    if (!confirm("Clear flagged status for this user?")) return;
    try {
        const res = await send(`/admin/users/${id}/unflag`, 'POST');
        if (res.ok) {
            loadAdminUsers();
        } else {
            alert(await parseErrorMessage(res, "Failed to unflag user."));
        }
    } catch (e) {
        console.error("Unflag error:", e);
    }
};

window.deleteUser = async function(id) {
    if (!confirm("Are you sure you want to delete this user? This cannot be undone.")) return;

    try {
        const res = await send(`/admin/delete-user/${id}`, 'DELETE');
        if (res.ok) {
            loadAdminUsers();
        } else {
            alert("Failed to delete user: " + await parseErrorMessage(res));
        }
    } catch (e) {
        console.error(e);
        alert("Error deleting user: " + e.message);
    }
};

window.openEditUserModal = function(userOrId) {
    const user = (typeof userOrId === 'object' && userOrId !== null)
        ? userOrId
        : adminUsersMap.get(Number(userOrId));

    if (!user) {
        alert("Could not find user details.");
        return;
    }

    const deptCode = (user.department && user.department.code)
        || (user.departmentCode && user.departmentCode !== "UNKNOWN" ? user.departmentCode : "");

    setVal("editUserId", user.id);
    setVal("editName", user.name || "");
    setVal("editEmail", user.email || "");
    setVal("editRole", user.role || "STUDENT");
    setVal("editDept", deptCode);
    setVal("editBatch", user.batchYear || "");
    setVal("editEnrollment", user.enrollmentNumber || "");
    setVal("editHeadline", user.headline || "");
    setVal("editCompany", user.currentCompany || "");
    setVal("editDesignation", user.designation || "");
    setVal("editSkills", user.skills || "");
    setVal("editLinkedin", user.linkedinUrl || "");
    setVal("editGithub", user.githubUrl || "");
    setVal("editBio", user.pastExperience || "");

    const modalEl = document.getElementById('editUserModal');
    if (modalEl) {
        bootstrap.Modal.getOrCreateInstance(modalEl).show();
    }
};

window.adminSaveUser = async function() {
    const id = getVal("editUserId");
    const batchRaw = getVal("editBatch");
    const data = {
        name: getVal("editName"),
        email: getVal("editEmail"),
        role: getVal("editRole") || "STUDENT",
        departmentCode: getVal("editDept") || null,
        batchYear: batchRaw ? parseInt(batchRaw, 10) : null,
        enrollmentNumber: getVal("editEnrollment"),
        headline: getVal("editHeadline"),
        currentCompany: getVal("editCompany"),
        designation: getVal("editDesignation"),
        skills: getVal("editSkills"),
        linkedinUrl: getVal("editLinkedin"),
        githubUrl: getVal("editGithub"),
        pastExperience: getVal("editBio")
    };

    if (!data.name || !data.email) {
        alert("Name and Email are required!");
        return;
    }

    const btn = document.querySelector("button[onclick='window.adminSaveUser()']");
    if (btn) { btn.innerHTML = "Saving..."; btn.disabled = true; }

    try {
        const response = await send(`/admin/users/${id}`, 'PUT', data);

        if (response.ok) {
            alert("✅ User updated successfully!");
            const modalEl = document.getElementById('editUserModal');
            if (modalEl) bootstrap.Modal.getOrCreateInstance(modalEl).hide();
            loadAdminUsers();
        } else {
            alert("❌ Failed to update user: " + await parseErrorMessage(response));
        }
    } catch (e) {
        console.error(e);
        alert("❌ Error: " + e.message);
    } finally {
        if (btn) {
            btn.innerHTML = '<i class="fas fa-save me-1"></i> Save Changes';
            btn.disabled = false;
        }
    }
};

window.adminResetPassword = async function() {
    const userId = getVal("editUserId");

    if (!userId) {
        alert("Error: No user selected.");
        return;
    }

    if (!confirm("⚠️ Are you sure? This will reset the user's password to 'Bvicam@2025'.")) {
        return;
    }

    const btn = document.querySelector("button[onclick='window.adminResetPassword()']");
    if (btn) { btn.innerHTML = "Resetting..."; btn.disabled = true; }

    try {
        const res = await send(`/admin/users/${userId}/reset-password`, 'PUT');

        if (res.ok) {
            const msg = await res.text();
            alert(msg || "✅ Password has been reset to: Bvicam@2025");
        } else {
            alert("❌ Failed to reset password: " + await parseErrorMessage(res));
        }
    } catch (e) {
        console.error(e);
        alert("Error: " + e.message);
    } finally {
        if (btn) {
            btn.innerHTML = '<i class="fas fa-key me-1"></i> Reset Password';
            btn.disabled = false;
        }
    }
};

window.openCreateUserModal = function() {
    setVal("createName", "");
    setVal("createEmail", "");
    setVal("createRole", "STUDENT");
    setVal("createDept", "MCA");
    setVal("createBatch", "");
    setVal("createEnrollment", "");
    setVal("createHeadline", "");
    setVal("createCompany", "");
    setVal("createDesignation", "");
    setVal("createSkills", "");
    setVal("createLinkedin", "");
    setVal("createGithub", "");
    setVal("createBio", "");
    setVal("createPassword", "Bvicam@2025");

    const modalEl = document.getElementById('createUserModal');
    if (modalEl) {
        bootstrap.Modal.getOrCreateInstance(modalEl).show();
    }
};

window.adminCreateUser = async function() {
    const batchRaw = getVal("createBatch");
    const data = {
        name: getVal("createName"),
        email: getVal("createEmail"),
        role: getVal("createRole") || "STUDENT",
        departmentCode: getVal("createDept") || "MCA",
        batchYear: batchRaw ? parseInt(batchRaw, 10) : null,
        enrollmentNumber: getVal("createEnrollment"),
        headline: getVal("createHeadline"),
        currentCompany: getVal("createCompany"),
        designation: getVal("createDesignation"),
        skills: getVal("createSkills"),
        linkedinUrl: getVal("createLinkedin"),
        githubUrl: getVal("createGithub"),
        pastExperience: getVal("createBio"),
        password: getVal("createPassword") || "Bvicam@2025"
    };

    if (!data.name || !data.email) {
        alert("Name and Email are required!");
        return;
    }

    const btn = document.querySelector("button[onclick='window.adminCreateUser()']")
             || document.querySelector("button[onclick='window.submitCreateUser()']")
             || document.querySelector("button[onclick='adminCreateUser()']");

    if (btn) { btn.innerText = "Creating..."; btn.disabled = true; }

    try {
        const response = await send('/admin/users', 'POST', data);

        if (response.ok) {
            alert("✅ User Created Successfully!");
            const modalEl = document.getElementById('createUserModal');
            if (modalEl) bootstrap.Modal.getOrCreateInstance(modalEl).hide();
            loadAdminUsers();
        } else {
            alert(`❌ Server Error (${response.status}):\n${await parseErrorMessage(response)}`);
        }
    } catch (e) {
        console.error(e);
        alert("Network Error: " + e.message);
    } finally {
        if (btn) { btn.innerHTML = '<i class="fas fa-check me-1"></i> Create User'; btn.disabled = false; }
    }
};

window.submitCreateUser = window.adminCreateUser;

window.uploadBulkDataExcel = async function() {
    const fileInput = document.getElementById("bulkFileExcel");
    const file = fileInput?.files[0];
    if (!file) return alert("⚠️ Please select an Excel (.xlsx) or CSV (.csv) file!");

    const btn = document.querySelector("button[onclick='window.uploadBulkDataExcel()']")
             || document.querySelector("button[onclick='uploadBulkDataExcel()']");

    if (btn) { btn.innerHTML = "Processing..."; btn.disabled = true; }

    const formData = new FormData();
    formData.append("file", file);

    try {
        const token = localStorage.getItem("jwt_token");
        const res = await fetch(`/api/admin/upload-users-excel`, {
            method: "POST",
            headers: { "Authorization": `Bearer ${token}` },
            body: formData
        });

        if (res.ok) {
            alert(await res.text());
            fileInput.value = "";
            loadAdminUsers();
        } else {
            alert("❌ Upload Failed: " + await parseErrorMessage(res));
        }
    } catch (e) {
        alert("❌ Error: " + e.message);
    } finally {
        if (btn) { btn.innerHTML = "<i class='fas fa-upload me-1'></i> Upload .xlsx / .csv"; btn.disabled = false; }
    }
};

window.sendBroadcastEmail = async function() {
    const subject = getVal("broadcastSubject");
    const body = getVal("broadcastMessage");

    if (!subject || !body) {
        alert("Please provide both an Email Subject and Message Content.");
        return;
    }

    const btn = document.getElementById("btnBroadcastEmail")
             || document.querySelector("button[onclick='window.sendBroadcastEmail()']");
    if (btn) { btn.innerHTML = "Sending Broadcast..."; btn.disabled = true; }

    try {
        const res = await send('/admin/broadcast', 'POST', { subject, body });
        if (res.ok) {
            const msg = await res.text();
            alert("📨 " + (msg || "Broadcast started in background!"));
            setVal("broadcastSubject", "");
            setVal("broadcastMessage", "");
        } else {
            alert("❌ Failed to send broadcast: " + await parseErrorMessage(res));
        }
    } catch (e) {
        console.error("Broadcast error:", e);
        alert("❌ Error sending broadcast: " + e.message);
    } finally {
        if (btn) {
            btn.innerHTML = '<i class="fas fa-bullhorn me-1"></i> Send Broadcast to Community';
            btn.disabled = false;
        }
    }
};

window.loadAdminFeedback = async function() {
    const tbody = document.getElementById("adminFeedbackTable");
    if (!tbody) return;

    tbody.innerHTML = "<tr><td colspan='4' class='text-center py-4'>Loading...</td></tr>";

    try {
        const feedbacks = await get('/feedback');
        tbody.innerHTML = "";

        if (!feedbacks || feedbacks.length === 0) {
            tbody.innerHTML = "<tr><td colspan='4' class='text-center py-4 text-muted'>No feedback yet.</td></tr>";
            return;
        }

        feedbacks.forEach(f => {
            const ts = f.submittedAt || f.createdAt;
            const dateStr = ts ? new Date(ts).toLocaleDateString() : '-';
            const ratingCount = Math.min(5, Math.max(0, Number(f.rating) || 0));
            tbody.insertAdjacentHTML("beforeend", `
                <tr>
                    <td>${escapeHtml(f.submittedBy?.name || 'Anonymous')}</td>
                    <td class="text-warning">${"⭐".repeat(ratingCount)}</td>
                    <td>${escapeHtml(f.comments || '')}</td>
                    <td>${escapeHtml(dateStr)}</td>
                </tr>`);
        });
    } catch (e) {
        tbody.innerHTML = "<tr><td colspan='4' class='text-danger text-center'>Error loading feedback</td></tr>";
    }
};

window.loadAdminJobs = async function() {
    const tbody = document.getElementById("adminJobsTable");
    if (!tbody) return;

    tbody.innerHTML = "<tr><td colspan='6' class='text-center py-4'>Loading jobs & referrals...</td></tr>";

    try {
        const jobs = await get('/jobs');
        tbody.innerHTML = "";

        if (!jobs || jobs.length === 0) {
            tbody.innerHTML = "<tr><td colspan='6' class='text-center py-4 text-muted'>No jobs or referrals posted yet. Click '+ Post Job / Referral' above to add one.</td></tr>";
            return;
        }

        jobs.forEach(j => {
            const postedBy = j.postedBy?.name || 'Admin';
            tbody.insertAdjacentHTML("beforeend", `
                <tr>
                    <td>${Number(j.id) || 0}</td>
                    <td>
                        <div class="fw-bold">${escapeHtml(j.title || '-')}</div>
                        <small class="text-primary fw-semibold">${escapeHtml(j.company || '-')}</small>
                        <div class="small text-muted text-truncate" style="max-width: 280px;">${escapeHtml(j.description || '')}</div>
                    </td>
                    <td>${escapeHtml(j.location || 'Remote')}</td>
                    <td><small class="text-break">${escapeHtml(j.applyLink || '-')}</small></td>
                    <td><span class="badge bg-light text-dark border">${escapeHtml(postedBy)}</span></td>
                    <td class="text-end text-nowrap">
                        <button class="btn btn-sm btn-outline-primary me-1" onclick="window.openEditJobModal(${j.id})" title="Edit Job / Referral">
                            <i class="fas fa-edit me-1"></i> Edit
                        </button>
                        <button class="btn btn-sm btn-outline-danger" onclick="window.deleteJob(${j.id})" title="Delete Job / Referral">
                            <i class="fas fa-trash"></i>
                        </button>
                    </td>
                </tr>`);
        });
    } catch (e) {
        console.error("Failed to load admin jobs:", e);
        tbody.innerHTML = "<tr><td colspan='6' class='text-danger text-center'>Error loading jobs & referrals</td></tr>";
    }
};

let allKnowledgeDocs = [];
const kbFilterState = {
    category: "ALL",
    sourceType: "ALL",
    syncStatus: "ALL",
    search: "",
    page: 1,
    pageSize: "50"
};

function updateKbCategoryKpis(docs) {
    const counts = { total: docs.length, NOTICE: 0, COURSE_MATERIAL: 0, SYLLABUS: 0, BVICAM_WEB: 0 };
    docs.forEach(d => {
        if (d.category === "NOTICE") counts.NOTICE++;
        else if (d.category === "COURSE_MATERIAL") counts.COURSE_MATERIAL++;
        else if (d.category === "SYLLABUS") counts.SYLLABUS++;
        else counts.BVICAM_WEB++;
    });
    const setTxt = (id, v) => { const el = document.getElementById(id); if (el) el.textContent = String(v); };
    setTxt("kbCountTotal", counts.total);
    setTxt("kbCountNotice", counts.NOTICE);
    setTxt("kbCountMaterial", counts.COURSE_MATERIAL);
    setTxt("kbCountSyllabus", counts.SYLLABUS);
    setTxt("kbCountWeb", counts.BVICAM_WEB);

    document.querySelectorAll(".kb-kpi-btn").forEach(btn => {
        const active = btn.getAttribute("data-kb-cat") === kbFilterState.category;
        btn.classList.toggle("border-primary", active);
        btn.classList.toggle("border-2", active);
        btn.classList.toggle("bg-primary-subtle", active);
    });
}

function renderKbPaginationBar(el, totalFiltered, totalAll, page, totalPages, startIdx, endIdx) {
    if (!el) return;
    if (totalFiltered === 0) {
        el.innerHTML = `<span>0 matching documents (${totalAll} total in knowledge base)</span>`;
        return;
    }
    const showAll = kbFilterState.pageSize === "ALL";
    const info = `<span>Showing <strong>${startIdx + 1}</strong>–<strong>${endIdx}</strong> of <strong>${totalFiltered}</strong> matching (${totalAll} total indexed)</span>`;
    if (showAll || totalPages <= 1) {
        el.innerHTML = info;
        return;
    }

    const pages = [];
    const maxButtons = 5;
    let first = Math.max(1, page - 2);
    let last = Math.min(totalPages, first + maxButtons - 1);
    if (last - first < maxButtons - 1) {
        first = Math.max(1, last - maxButtons + 1);
    }
    for (let p = first; p <= last; p++) {
        pages.push(`<li class="page-item ${p === page ? "active" : ""}"><button type="button" class="page-link" onclick="window.setKbPage(${p})">${p}</button></li>`);
    }

    el.innerHTML = `${info}
        <nav aria-label="Knowledge documents pagination">
            <ul class="pagination pagination-sm mb-0">
                <li class="page-item ${page <= 1 ? "disabled" : ""}">
                    <button type="button" class="page-link" onclick="window.setKbPage(${page - 1})">&laquo;</button>
                </li>
                ${pages.join("")}
                <li class="page-item ${page >= totalPages ? "disabled" : ""}">
                    <button type="button" class="page-link" onclick="window.setKbPage(${page + 1})">&raquo;</button>
                </li>
            </ul>
        </nav>`;
}

function renderKnowledgeDocsTable() {
    const tbody = document.getElementById("adminKnowledgeTable");
    if (!tbody) return;

    updateKbCategoryKpis(allKnowledgeDocs);

    const q = kbFilterState.search.toLowerCase();
    const filtered = allKnowledgeDocs.filter(doc => {
        if (kbFilterState.category !== "ALL" && doc.category !== kbFilterState.category) return false;
        if (kbFilterState.sourceType !== "ALL" && doc.sourceType !== kbFilterState.sourceType) return false;
        if (kbFilterState.syncStatus !== "ALL" && (doc.syncStatus || "INDEXED") !== kbFilterState.syncStatus) return false;
        if (q) {
            const hay = `${doc.id ?? ""} ${doc.title ?? ""} ${doc.originalFileName ?? ""} ${doc.sourceUrl ?? ""} ${doc.publishedDate ?? ""} ${doc.category ?? ""}`.toLowerCase();
            if (!hay.includes(q)) return false;
        }
        return true;
    });

    const summaryEl = document.getElementById("kbExplorerSummary");
    if (summaryEl) {
        summaryEl.textContent = `${filtered.length} matching documents out of ${allKnowledgeDocs.length} indexed in GradBot.`;
    }

    const topNav = document.getElementById("kbPaginationTop");
    const bottomNav = document.getElementById("kbPaginationBottom");

    if (filtered.length === 0) {
        renderKbPaginationBar(topNav, 0, allKnowledgeDocs.length, 1, 1, 0, 0);
        renderKbPaginationBar(bottomNav, 0, allKnowledgeDocs.length, 1, 1, 0, 0);
        tbody.innerHTML = allKnowledgeDocs.length === 0
            ? "<tr><td colspan='7' class='text-center py-4 text-muted'>No documents or URLs indexed yet. Click Sync All above to fetch BVICAM notices, syllabi &amp; course materials.</td></tr>"
            : "<tr><td colspan='7' class='text-center py-4 text-muted'>No documents match the current search/filter criteria.</td></tr>";
        return;
    }

    const showAll = kbFilterState.pageSize === "ALL";
    const pageSizeNum = showAll ? filtered.length : Math.max(10, Number(kbFilterState.pageSize) || 50);
    const totalPages = Math.max(1, Math.ceil(filtered.length / pageSizeNum));
    if (kbFilterState.page > totalPages) kbFilterState.page = totalPages;
    if (kbFilterState.page < 1) kbFilterState.page = 1;

    const startIdx = showAll ? 0 : (kbFilterState.page - 1) * pageSizeNum;
    const endIdx = showAll ? filtered.length : Math.min(filtered.length, startIdx + pageSizeNum);
    const visibleDocs = filtered.slice(startIdx, endIdx);

    renderKbPaginationBar(topNav, filtered.length, allKnowledgeDocs.length, kbFilterState.page, totalPages, startIdx, endIdx);
    renderKbPaginationBar(bottomNav, filtered.length, allKnowledgeDocs.length, kbFilterState.page, totalPages, startIdx, endIdx);

    const rowsHtml = visibleDocs.map(doc => {
        const dateStr = doc.createdAt ? new Date(doc.createdAt).toLocaleString() : '-';
        const catBadgeClass = doc.category === 'NOTICE' ? 'bg-danger' :
                              doc.category === 'SYLLABUS' ? 'bg-info text-dark' :
                              doc.category === 'COURSE_MATERIAL' ? 'bg-warning text-dark' :
                              doc.category === 'BVICAM_WEB' ? 'bg-success' : 'bg-secondary';
        const sourceBadgeClass = doc.sourceType === 'AUTO_SYNC' ? 'bg-primary' :
                                 doc.sourceType === 'WEB_URL' ? 'bg-info text-dark' : 'bg-dark';
        const syncState = doc.syncStatus || 'INDEXED';
        const syncBadgeClass = syncState === 'INDEXED' ? 'bg-success-subtle text-success border border-success-subtle' :
                               syncState === 'PENDING_QDRANT_SYNC' ? 'bg-warning-subtle text-dark border border-warning-subtle' :
                               'bg-secondary-subtle text-secondary';
        const validSourceUrl = safeHttpUrl(doc.sourceUrl);
        const sourceDisplay = validSourceUrl
            ? `<a href="${escapeHtml(validSourceUrl)}" target="_blank" rel="noopener noreferrer" class="small text-truncate d-block" style="max-width: 340px;">${escapeHtml(validSourceUrl)}</a>`
            : (doc.sourceUrl ? `<span class="small text-muted text-truncate d-block" style="max-width: 340px;">${escapeHtml(doc.sourceUrl)}</span>` : '');

        return `
            <tr>
                <td class="text-muted fw-semibold">#${Number(doc.id) || 0}</td>
                <td>
                    <div class="fw-bold text-dark">${escapeHtml(doc.title || '-')}</div>
                    <div class="d-flex flex-wrap gap-2 align-items-center mt-1">
                        ${doc.publishedDate ? `<span class="badge bg-light text-dark border"><i class="far fa-calendar-alt me-1"></i>${escapeHtml(doc.publishedDate)}</span>` : ''}
                        ${doc.originalFileName ? `<small class="text-muted"><i class="fas fa-file-pdf me-1 text-danger"></i>${escapeHtml(doc.originalFileName)}</small>` : ''}
                    </div>
                    ${sourceDisplay}
                </td>
                <td><span class="badge ${catBadgeClass}">${escapeHtml(doc.category || 'GENERAL')}</span></td>
                <td>
                    <div><span class="badge ${sourceBadgeClass}">${escapeHtml(doc.sourceType || 'FILE_UPLOAD')}</span></div>
                    <div class="mt-1"><span class="badge ${syncBadgeClass}" style="font-size: 0.7rem;">${escapeHtml(syncState)}</span></div>
                </td>
                <td><span class="badge bg-light text-dark border">${Number(doc.chunkCount) || 0} chunks</span></td>
                <td><small class="text-muted">${escapeHtml(dateStr)}</small></td>
                <td class="text-end">
                    <button class="btn btn-sm btn-outline-danger" onclick="window.deleteKnowledgeDocument(${Number(doc.id) || 0})" title="Delete Document &amp; Chunks">
                        <i class="fas fa-trash"></i>
                    </button>
                </td>
            </tr>`;
    }).join("");

    tbody.innerHTML = rowsHtml;
}

window.setKbCategoryFilter = function(category) {
    kbFilterState.category = category || "ALL";
    kbFilterState.page = 1;
    const catSelect = document.getElementById("kbCategoryFilter");
    if (catSelect) catSelect.value = kbFilterState.category;
    renderKnowledgeDocsTable();
};

window.setKbFilter = function(field, value) {
    kbFilterState[field] = value;
    if (field !== "page") kbFilterState.page = 1;
    renderKnowledgeDocsTable();
};

window.setKbPage = function(page) {
    kbFilterState.page = page;
    renderKnowledgeDocsTable();
};

window.loadAdminKnowledge = async function(options = {}) {
    if (!options.skipStatus) {
        loadBvicamSyncStatus();
    }
    const tbody = document.getElementById("adminKnowledgeTable");
    if (!tbody) return;

    if (!options.silent) {
        tbody.innerHTML = "<tr><td colspan='7' class='text-center py-4'>Loading indexed documents...</td></tr>";
    }

    try {
        const docs = await get('/admin/knowledge/documents');
        allKnowledgeDocs = Array.isArray(docs) ? docs : [];
        renderKnowledgeDocsTable();
    } catch (e) {
        console.error("Failed to load knowledge documents:", e);
        if (!options.silent) {
            tbody.innerHTML = "<tr><td colspan='7' class='text-danger text-center'>Error loading knowledge sources</td></tr>";
        }
    }
};

window.uploadKnowledgeDocument = async function() {
    const fileInput = document.getElementById("knowledgeFileInput");
    const file = fileInput?.files[0];
    if (!file) {
        alert("⚠️ Please select a file (.pdf, .png, .jpg, .txt, .md) to upload!");
        return;
    }

    const title = getVal("knowledgeDocTitle");
    const category = getVal("knowledgeDocCategory") || "NOTICE";
    const btn = document.getElementById("btnUploadDoc");

    if (btn) { btn.innerHTML = "<i class='fas fa-spinner fa-spin me-1'></i> Processing & Vectorizing..."; btn.disabled = true; }

    const formData = new FormData();
    formData.append("file", file);
    if (title) formData.append("title", title);
    formData.append("category", category);

    try {
        const token = localStorage.getItem("jwt_token");
        const res = await fetch('/api/admin/knowledge/upload', {
            method: "POST",
            headers: { "Authorization": `Bearer ${token}` },
            body: formData
        });

        if (res.ok) {
            const data = await res.json();
            alert(`✅ Document "${data.title}" successfully indexed into ${data.chunkCount} vector chunks!`);
            fileInput.value = "";
            setVal("knowledgeDocTitle", "");
            window.loadAdminKnowledge();
        } else {
            alert("❌ Upload Failed: " + await parseErrorMessage(res));
        }
    } catch (e) {
        console.error(e);
        alert("❌ Error: " + e.message);
    } finally {
        if (btn) { btn.innerHTML = "<i class='fas fa-upload me-1'></i> Upload & Vectorize"; btn.disabled = false; }
    }
};

window.scrapeKnowledgeUrl = async function() {
    const url = getVal("knowledgeScrapeUrl");
    if (!url || (!url.startsWith("http://") && !url.startsWith("https://"))) {
        alert("⚠️ Please enter a valid URL starting with http:// or https://");
        return;
    }

    const title = getVal("knowledgeScrapeTitle");
    const category = getVal("knowledgeScrapeCategory") || "BVICAM_WEB";
    const btn = document.getElementById("btnScrapeUrl");

    if (btn) { btn.innerHTML = "<i class='fas fa-spinner fa-spin me-1'></i> Scraping & Vectorizing..."; btn.disabled = true; }

    try {
        const res = await send('/admin/knowledge/scrape-url', 'POST', { url, title, category });
        if (res.ok) {
            const data = await res.json();
            alert(`✅ URL successfully scraped and indexed as "${data.title}" (${data.chunkCount} chunks)!`);
            setVal("knowledgeScrapeUrl", "");
            setVal("knowledgeScrapeTitle", "");
            window.loadAdminKnowledge();
        } else {
            alert("❌ Scraping Failed: " + await parseErrorMessage(res));
        }
    } catch (e) {
        console.error(e);
        alert("❌ Error: " + e.message);
    } finally {
        if (btn) { btn.innerHTML = "<i class='fas fa-spider me-1'></i> Scrape & Index URL"; btn.disabled = false; }
    }
};

window.deleteKnowledgeDocument = async function(id) {
    if (!confirm(`Are you sure you want to delete document #${id}? All associated vector chunks will be permanently removed.`)) {
        return;
    }

    try {
        const res = await send(`/admin/knowledge/documents/${id}`, 'DELETE');
        if (res.ok) {
            alert("✅ Document deleted successfully.");
            window.loadAdminKnowledge();
        } else {
            alert("❌ Delete failed: " + await parseErrorMessage(res));
        }
    } catch (e) {
        console.error(e);
        alert("❌ Error deleting document: " + e.message);
    }
};

let bvicamSyncPollTimer = null;
let bvicamWasRunning = false;
let bvicamPollTicks = 0;

function renderBvicamSyncStatus(status) {
    if (!status) return;
    const badgeEl = document.getElementById("bvicamSyncBadge");
    const phaseEl = document.getElementById("syncMetricPhase");
    const activityEl = document.getElementById("bvicamSyncActivity");
    const discoveredEl = document.getElementById("syncMetricDiscovered");
    const newEl = document.getElementById("syncMetricNew");
    const scannedEl = document.getElementById("syncMetricScanned");
    const updatedEl = document.getElementById("syncMetricUpdated");
    const skippedEl = document.getElementById("syncMetricSkipped");
    const failedEl = document.getElementById("syncMetricFailed");
    const msgEl = document.getElementById("bvicamSyncMessage");
    const issuesBox = document.getElementById("bvicamSyncIssuesBox");
    const issuesList = document.getElementById("bvicamSyncIssuesList");

    const phase = status.phase || "IDLE";
    if (badgeEl) {
        badgeEl.textContent = status.running ? `RUNNING (${phase})` : phase;
        badgeEl.className = "badge " + (
            status.running ? "bg-primary" :
            phase === "COMPLETED" ? "bg-success" :
            phase.startsWith("ABORTED") || phase.startsWith("STOPPED") ? "bg-warning text-dark" : "bg-secondary"
        );
    }
    if (phaseEl) phaseEl.textContent = phase;
    if (activityEl) activityEl.textContent = status.currentActivity || (status.running ? phase : "Idle");
    if (discoveredEl) discoveredEl.textContent = String(status.discoveredCount ?? 0);
    if (newEl) newEl.textContent = String(status.indexedNewCount ?? 0);
    if (scannedEl) scannedEl.textContent = String(status.scannedFallbackCount ?? 0);
    if (updatedEl) updatedEl.textContent = String(status.updatedCount ?? 0);
    if (skippedEl) skippedEl.textContent = String(status.skippedExistingCount ?? 0);
    if (failedEl) failedEl.textContent = String(status.failedCount ?? 0);

    if (msgEl) {
        let text = status.lastMessage || "Ready for scheduled 24h BVICAM sync.";
        if (status.stoppedReason) {
            text += ` (Note: ${status.stoppedReason})`;
        }
        msgEl.textContent = text;
    }

    if (issuesBox && issuesList) {
        const issues = Array.isArray(status.recentIssues) ? status.recentIssues : [];
        if (issues.length > 0) {
            issuesBox.classList.remove("d-none");
            issuesList.innerHTML = issues.map(item => `<li>${escapeHtml(String(item))}</li>`).join("");
        } else {
            issuesBox.classList.add("d-none");
            issuesList.innerHTML = "";
        }
    }

    if (status.running) {
        bvicamWasRunning = true;
        if (!bvicamSyncPollTimer) {
            bvicamPollTicks = 0;
            bvicamSyncPollTimer = setInterval(() => {
                bvicamPollTicks++;
                window.loadBvicamSyncStatus();
                if (bvicamPollTicks % 4 === 0 && typeof window.loadAdminKnowledge === "function") {
                    window.loadAdminKnowledge({ silent: true, skipStatus: true });
                }
            }, 3000);
        }
    } else {
        if (bvicamSyncPollTimer) {
            clearInterval(bvicamSyncPollTimer);
            bvicamSyncPollTimer = null;
        }
        if (bvicamWasRunning) {
            bvicamWasRunning = false;
            if (typeof window.loadAdminKnowledge === "function") {
                window.loadAdminKnowledge({ silent: true, skipStatus: true });
            }
        }
    }
}

window.loadBvicamSyncStatus = async function() {
    try {
        const status = await get("/admin/knowledge/sync-status");
        renderBvicamSyncStatus(status);
    } catch (e) {
        const msgEl = document.getElementById("bvicamSyncMessage");
        if (msgEl) msgEl.textContent = "Unable to load BVICAM sync status: " + e.message;
    }
};

window.triggerBvicamSync = async function(mode = "FULL", btnEl = null) {
    const origHtml = btnEl ? btnEl.innerHTML : null;
    if (btnEl) {
        btnEl.disabled = true;
        btnEl.innerHTML = `<span class="spinner-border spinner-border-sm me-1"></span>Starting...`;
    }
    try {
        const res = await send(`/admin/knowledge/sync-bvicam?mode=${encodeURIComponent(mode)}`, "POST");
        const bodyText = await res.text();
        let payload = null;
        try {
            payload = bodyText ? JSON.parse(bodyText) : null;
        } catch (_) {
            payload = null;
        }

        if (res.status === 409 && payload && payload.status) {
            renderBvicamSyncStatus(payload.status);
            alert(payload.message || "A BVICAM portal sync is already running.");
            return;
        }

        if (!res.ok) {
            const errMsg = (payload && (payload.error || payload.message)) || bodyText || `Sync failed (${res.status})`;
            throw new Error(errMsg);
        }

        if (payload && payload.status) {
            bvicamWasRunning = true;
            renderBvicamSyncStatus({ ...payload.status, running: true });
        } else {
            await window.loadBvicamSyncStatus();
        }
    } catch (e) {
        console.error(e);
        alert("❌ Could not start BVICAM sync: " + e.message);
    } finally {
        if (btnEl && origHtml !== null) {
            btnEl.disabled = false;
            btnEl.innerHTML = origHtml;
        }
    }
};
