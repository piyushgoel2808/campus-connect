/**
 * ============================================================================
 * FILE: src/main/resources/static/js/modules/admin.js
 * PURPOSE: Admin Control Panel module (`components/admin.html`).
 *
 * OVERVIEW:
 *   Provides administrative capabilities for users with the `ADMIN` role:
 *     1. User Management: List all registered users, inspect profanity flag
 *        counts, clear flags (`unflagUser`), create single users (`adminCreateUser`),
 *        edit full user profiles (`adminSaveUser`), reset passwords to default
 *        (`adminResetPassword`), and delete users (`deleteUser`).
 *     2. Bulk User Import: Upload `.xlsx` or `.csv` files (`uploadBulkDataExcel`)
 *        for batch user provisioning.
 *     3. Community Broadcast: Send an email announcement to all registered
 *        community members (`sendBroadcastEmail`).
 *     4. Feedback Viewer: View star ratings and comments submitted by users
 *        (`loadAdminFeedback`).
 *     5. Jobs & Referrals Management: View, edit, and delete all job/referral
 *        postings (`loadAdminJobs`, delegating modal actions to `jobs.js`).
 *
 * API ENDPOINTS USED:
 *   - GET    `/api/users`                         : Fetch all users
 *   - POST   `/api/admin/users`                   : Create a new user
 *   - PUT    `/api/admin/users/{id}`              : Update an existing user
 *   - PUT    `/api/admin/users/{id}/reset-password`: Reset password to `Bvicam@2025`
 *   - POST   `/api/admin/users/{id}/unflag`       : Clear profanity flag status
 *   - DELETE `/api/admin/delete-user/{id}`        : Permanently delete a user
 *   - POST   `/api/admin/upload-users-excel`      : Multipart bulk Excel/CSV import
 *   - POST   `/api/admin/broadcast`               : Trigger mass email broadcast
 *   - GET    `/api/feedback`                      : Fetch all user feedback entries
 *   - GET    `/api/jobs`                          : Fetch all jobs for admin table
 * ============================================================================
 */

import { get, send, getCurrentUser } from '../utils/api.js';

// In-memory cache mapping userId -> user object for quick modal population
const adminUsersMap = new Map();

/**
 * Helper: Safely sets the `.value` of an input element by DOM ID.
 * @param {string} id - DOM element ID.
 * @param {any} val   - Value to assign (defaults to empty string if null/undefined).
 */
const setVal = (id, val) => {
    const el = document.getElementById(id);
    if (el) el.value = val ?? "";
};

/**
 * Helper: Safely reads and trims the `.value` of an input element by DOM ID.
 * @param {string} id - DOM element ID.
 * @returns {string} Trimmed input value or empty string if element is missing.
 */
const getVal = (id) => {
    const el = document.getElementById(id);
    return el ? el.value.trim() : "";
};

// =========================================================
// 1. INITIALIZATION
// =========================================================

/**
 * Entry point invoked by `dashboard-main.js` when the Admin tab (`#tab-admin`) is loaded.
 * Verifies ADMIN privileges, loads the initial Users and Jobs tables, and wires up
 * click listeners on the Admin sub-navigation pills.
 */
export function loadAdminModule() {
    // 1. Check Permissions
    const { role } = getCurrentUser();
    if (!role || role.toUpperCase() !== 'ADMIN') {
        console.warn("Unauthorized access to Admin module");
        return;
    }

    // 2. Load Initial Data (Users & Jobs)
    loadAdminUsers();
    window.loadAdminJobs();

    // 3. Attach Event Listeners for Sub-tabs (Users, Jobs & Referrals, User Feedback)
    setTimeout(() => {
        const feedbackBtn = document.getElementById('tab-feedback-btn');
        const usersBtn = document.getElementById('tab-users-btn');
        const jobsBtn = document.getElementById('tab-jobs-admin-btn');

        if (feedbackBtn) feedbackBtn.onclick = loadAdminFeedback;
        if (usersBtn) usersBtn.onclick = loadAdminUsers;
        if (jobsBtn) jobsBtn.onclick = window.loadAdminJobs;
    }, 100);
}

// =========================================================
// 2. USER MANAGEMENT (READ / DELETE / EDIT)
// =========================================================

/**
 * Fetches all users from `GET /api/users`, caches them in `adminUsersMap`,
 * and renders rows inside `#adminUserTable` including profanity moderation
 * badges and Unflag / Edit / Delete action buttons.
 */
async function loadAdminUsers() {
    try {
        const users = await get('/users');
        const tbody = document.getElementById("adminUserTable");
        if (!tbody) return;

        // Refresh in-memory map so `openEditUserModal(id)` can look up users by ID
        adminUsersMap.clear();
        users.forEach(u => adminUsersMap.set(u.id, u));

        tbody.innerHTML = users.map(u => {
            // Render moderation status badge (Flagged with count vs Clean)
            const statusBadge = u.isFlagged 
                ? `<span class="cc-flag-badge"><i class="fas fa-flag"></i> Flagged (${u.flagCount || 1}x)</span>`
                : `<span class="badge bg-success bg-opacity-10 text-success border border-success border-opacity-25"><i class="fas fa-check-circle me-1"></i> Clean</span>`;

            // Show quick Unflag button only if user is currently flagged for profanity
            const unflagBtn = u.isFlagged
                ? `<button class="btn btn-sm btn-outline-warning me-1" title="Clear Profanity Flags" onclick="window.unflagUser(${u.id})">
                     <i class="fas fa-flag-checkered me-1"></i> Unflag
                   </button>`
                : "";

            return `
            <tr>
                <td>${u.id}</td>
                <td>
                    <div class="fw-semibold">${u.name}</div>
                    <small class="text-muted">${u.departmentCode || u.departmentName || ''} ${u.batchYear ? '• Batch ' + u.batchYear : ''}</small>
                </td>
                <td><span class="badge bg-light text-dark border">${u.role}</span></td>
                <td>${u.email}</td>
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

/**
 * Clears the profanity flag status and resets flag count for a specific user
 * via `POST /api/admin/users/{id}/unflag`.
 *
 * @param {number} id - Target user ID to unflag.
 */
window.unflagUser = async function(id) {
    if (!confirm("Clear flagged status for this user?")) return;
    try {
        const res = await send(`/admin/users/${id}/unflag`, 'POST');
        if (res.ok) {
            loadAdminUsers();
        } else {
            alert("Failed to unflag user.");
        }
    } catch(e) {
        console.error("Unflag error:", e);
    }
};

/**
 * Permanently deletes a user account via `DELETE /api/admin/delete-user/{id}`
 * after confirming with the administrator.
 *
 * @param {number} id - Target user ID to delete.
 */
window.deleteUser = async function(id) {
    if (!confirm("Are you sure you want to delete this user? This cannot be undone.")) return;

    try {
        const res = await send(`/admin/delete-user/${id}`, 'DELETE');
        if (res.ok) {
            loadAdminUsers();
        } else {
            const err = await res.text();
            alert("Failed to delete user: " + err);
        }
    } catch(e) {
        console.error(e);
        alert("Error deleting user: " + e.message);
    }
};

/**
 * Populates and opens the `#editUserModal` dialog for a given user ID or user object.
 *
 * @param {number|Object} userOrId - Either a user ID (looked up in `adminUsersMap`) or a user object.
 */
window.openEditUserModal = function(userOrId) {
    const user = (typeof userOrId === 'object' && userOrId !== null)
        ? userOrId
        : adminUsersMap.get(Number(userOrId));

    if (!user) {
        alert("Could not find user details.");
        return;
    }

    // Resolve department code from nested department object or flat DTO field
    const deptCode = (user.department && user.department.code)
        || (user.departmentCode && user.departmentCode !== "UNKNOWN" ? user.departmentCode : "");

    // Populate all form fields inside #editUserModal
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
    setVal("editExperience", user.pastExperience || "");

    const modalEl = document.getElementById('editUserModal');
    if (modalEl) {
        bootstrap.Modal.getOrCreateInstance(modalEl).show();
    }
};

/**
 * Reads updated user fields from `#editUserModal` and sends `PUT /api/admin/users/{id}`.
 * Refreshes the user table on success.
 */
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
        pastExperience: getVal("editBio") || getVal("editExperience")
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
            if (modalEl) bootstrap.Modal.getInstance(modalEl)?.hide();
            loadAdminUsers();
        } else {
            const text = await response.text();
            alert("❌ Failed to update user: " + text);
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

// =========================================================
// 3. RESET PASSWORD
// =========================================================

/**
 * Resets the currently edited user's password to the campus default (`Bvicam@2025`)
 * via `PUT /api/admin/users/{id}/reset-password`.
 */
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
            const text = await res.text();
            alert("❌ Failed to reset password: " + text);
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

// =========================================================
// 4. CREATE USER
// =========================================================

/**
 * Resets all fields in `#createUserModal` to clean defaults and opens the modal.
 */
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
        const modal = bootstrap.Modal.getInstance(modalEl) || new bootstrap.Modal(modalEl);
        modal.show();
    }
};

/**
 * Reads new user fields from `#createUserModal` and sends `POST /api/admin/users`.
 * Closes the modal and reloads `#adminUserTable` upon creation.
 */
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
            if (modalEl) bootstrap.Modal.getInstance(modalEl)?.hide();
            loadAdminUsers();
        } else {
            const errorText = await response.text();
            console.error("Server Error:", errorText);
            alert(`❌ Server Error (${response.status}):\n${errorText}`);
        }
    } catch (e) {
        console.error(e);
        alert("Network Error: " + e.message);
    } finally {
        if (btn) { btn.innerHTML = '<i class="fas fa-check me-1"></i> Create User'; btn.disabled = false; }
    }
};

// Alias for backward compatibility with inline onclick handlers
window.submitCreateUser = window.adminCreateUser;

// =========================================================
// 5. BULK UPLOAD (EXCEL / CSV)
// =========================================================

/**
 * Uploads an Excel (`.xlsx`) or CSV (`.csv`) file from `#bulkFileExcel` via
 * multipart `POST /api/admin/upload-users-excel` to batch-import user accounts.
 * Note: Uses native `fetch()` with `FormData` so the browser sets the multipart boundary.
 */
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

        const text = await res.text();

        if (res.ok) {
            alert(text);
            fileInput.value = "";
            loadAdminUsers();
        } else {
            alert("❌ Upload Failed: " + text);
        }

    } catch (e) {
        alert("❌ Error: " + e.message);
    } finally {
        if (btn) { btn.innerHTML = "<i class='fas fa-upload me-1'></i> Upload .xlsx / .csv"; btn.disabled = false; }
    }
};

// =========================================================
// 6. BROADCAST EMAIL
// =========================================================

/**
 * Sends a community-wide broadcast email via `POST /api/admin/broadcast`
 * using the subject (`#broadcastSubject`) and message (`#broadcastMessage`) inputs.
 */
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
            const err = await res.text();
            alert("❌ Failed to send broadcast: " + err);
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

// =========================================================
// 7. FEEDBACK MANAGEMENT
// =========================================================

/**
 * Fetches all user feedback submissions from `GET /api/feedback` and renders
 * them inside `#adminFeedbackTable` under the User Feedback sub-tab.
 */
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
            tbody.innerHTML += `
                <tr>
                    <td>${f.submittedBy?.name || 'Anonymous'}</td>
                    <td class="text-warning">${"⭐".repeat(f.rating || 0)}</td>
                    <td>${f.comments || ''}</td>
                    <td>${dateStr}</td>
                </tr>`;
        });
    } catch(e) {
        tbody.innerHTML = "<tr><td colspan='4' class='text-danger text-center'>Error loading feedback</td></tr>";
    }
};

// =========================================================
// 8. JOBS & REFERRALS MANAGEMENT (ADMIN)
// =========================================================

/**
 * Fetches all job and referral postings from `GET /api/jobs` and populates
 * `#adminJobsTable` with Edit and Delete controls (wired to `window.openEditJobModal`
 * and `window.deleteJob` in `jobs.js`).
 */
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
            tbody.innerHTML += `
                <tr>
                    <td>${j.id}</td>
                    <td>
                        <div class="fw-bold">${j.title || '-'}</div>
                        <small class="text-primary fw-semibold">${j.company || '-'}</small>
                        <div class="small text-muted text-truncate" style="max-width: 280px;">${j.description || ''}</div>
                    </td>
                    <td>${j.location || 'Remote'}</td>
                    <td><small class="text-break">${j.applyLink || '-'}</small></td>
                    <td><span class="badge bg-light text-dark border">${postedBy}</span></td>
                    <td class="text-end text-nowrap">
                        <button class="btn btn-sm btn-outline-primary me-1" onclick="window.openEditJobModal(${j.id})" title="Edit Job / Referral">
                            <i class="fas fa-edit me-1"></i> Edit
                        </button>
                        <button class="btn btn-sm btn-outline-danger" onclick="window.deleteJob(${j.id})" title="Delete Job / Referral">
                            <i class="fas fa-trash"></i>
                        </button>
                    </td>
                </tr>`;
        });
    } catch (e) {
        console.error("Failed to load admin jobs:", e);
        tbody.innerHTML = "<tr><td colspan='6' class='text-danger text-center'>Error loading jobs & referrals</td></tr>";
    }
};