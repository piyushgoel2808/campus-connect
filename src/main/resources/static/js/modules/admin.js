import { get, send, getCurrentUser } from '../utils/api.js';

// =========================================================
// 1. INITIALIZATION
// =========================================================

export function loadAdminModule() {
    // 1. Check Permissions
    const { role } = getCurrentUser();
    if (role !== 'ADMIN') {
        console.warn("Unauthorized access to Admin module");
        return;
    }

    // 2. Load Initial Data (Users)
    loadAdminUsers();

    // 3. Attach Event Listeners for Sub-tabs
    setTimeout(() => {
        const feedbackBtn = document.getElementById('tab-feedback-btn');
        const usersBtn = document.getElementById('tab-users-btn');

        if(feedbackBtn) feedbackBtn.onclick = loadAdminFeedback;
        if(usersBtn) usersBtn.onclick = loadAdminUsers;
    }, 100);
}

// =========================================================
// 2. USER MANAGEMENT (READ / DELETE / EDIT)
// =========================================================

async function loadAdminUsers() {
    try {
        const users = await get('/users');
        const tbody = document.getElementById("adminUserTable");
        if (!tbody) return;

        tbody.innerHTML = users.map(u => `
            <tr>
                <td>${u.id}</td>
                <td>${u.name}</td>
                <td>${u.role}</td>
                <td>${u.email}</td>
                <td>
                    <button class="btn btn-sm btn-outline-primary me-1"
                        onclick='window.openEditUserModal(${JSON.stringify(u)})'>
                        <i class="fas fa-edit"></i>
                    </button>
                    <button class="btn btn-sm btn-outline-danger"
                        onclick="window.deleteUser(${u.id})">
                        <i class="fas fa-trash"></i>
                    </button>
                </td>
            </tr>`).join('');
    } catch (e) {
        console.error("Failed to load users", e);
        const tbody = document.getElementById("adminUserTable");
        if(tbody) tbody.innerHTML = `<tr><td colspan="5" class="text-danger text-center">Error loading users</td></tr>`;
    }
}

window.deleteUser = async function(id) {
    if(!confirm("Are you sure? This cannot be undone.")) return;

    try {
        const res = await send(`/admin/delete-user/${id}`, 'DELETE');
        if(res.ok) {
            loadAdminUsers(); // Refresh list
        } else {
            alert("Failed to delete user.");
        }
    } catch(e) { console.error(e); }
};

window.openEditUserModal = function(user) {
    document.getElementById("editUserId").value = user.id;
    document.getElementById("editName").value = user.name || "";
    document.getElementById("editEmail").value = user.email || "";
    document.getElementById("editRole").value = user.role || "STUDENT";
    document.getElementById("editBatch").value = user.batchYear || "";
    document.getElementById("editEnrollment").value = user.enrollmentNumber || "";

    new bootstrap.Modal(document.getElementById('editUserModal')).show();
};

window.adminSaveUser = async function() {
    const id = document.getElementById("editUserId").value;
    const data = {
        name: document.getElementById("editName").value,
        email: document.getElementById("editEmail").value,
        role: document.getElementById("editRole").value,
        batchYear: document.getElementById("editBatch").value,
        enrollmentNumber: document.getElementById("editEnrollment").value,
    };

    const res = await send(`/admin/users/${id}`, 'PUT', data);

    if(res.ok) {
        bootstrap.Modal.getInstance(document.getElementById('editUserModal')).hide();
        loadAdminUsers();
    } else {
        alert("Failed to update user.");
    }
};

// =========================================================
// 3. RESET PASSWORD (NEW)
// =========================================================

window.adminResetPassword = async function() {
    const userId = document.getElementById("editUserId").value;

    if (!userId) {
        alert("Error: No user selected.");
        return;
    }

    // Confirmation (Aligned with Controller logic)
    if (!confirm("⚠️ Are you sure? This will reset the user's password to 'Bvicam@2025'.")) {
        return;
    }

    const btn = document.querySelector("button[onclick='window.adminResetPassword()']");
    if(btn) { btn.innerHTML = "Resetting..."; btn.disabled = true; }

    try {
        // Call Backend API (Using PUT as defined in Controller)
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
        if(btn) {
            btn.innerHTML = '<i class="fas fa-key"></i> Reset Password';
            btn.disabled = false;
        }
    }
};

// =========================================================
// 4. CREATE USER
// =========================================================

window.openCreateUserModal = function() {
    // Clear the form fields first
    document.getElementById("createName").value = "";
    document.getElementById("createEmail").value = "";
    document.getElementById("createRole").value = "STUDENT";
    document.getElementById("createBatch").value = "";
    document.getElementById("createEnrollment").value = "";

    // Show the modal
    new bootstrap.Modal(document.getElementById('createUserModal')).show();
};

window.adminCreateUser = async function() {
    const data = {
        name: document.getElementById("createName").value,
        email: document.getElementById("createEmail").value,
        role: document.getElementById("createRole").value,
        batchYear: parseInt(document.getElementById("createBatch").value) || null,
        enrollmentNumber: document.getElementById("createEnrollment").value,
        password: "password123" // Default password
    };

    if (!data.name || !data.email) {
        alert("Name and Email are required!");
        return;
    }

    const btn = document.querySelector("button[onclick='window.adminCreateUser()']")
             || document.querySelector("button[onclick='adminCreateUser()']");

    if(btn) { btn.innerText = "Creating..."; btn.disabled = true; }

    try {
        const token = localStorage.getItem("jwt_token");
        const response = await fetch('/api/admin/users', {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(data)
        });

        if (response.ok) {
            alert("✅ User Created Successfully!");
            const modalEl = document.getElementById('createUserModal');
            const modal = bootstrap.Modal.getInstance(modalEl);
            modal.hide();
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
        if(btn) { btn.innerText = "Create User"; btn.disabled = false; }
    }
};

// =========================================================
// 5. BULK UPLOAD (EXCEL)
// =========================================================

window.uploadBulkDataExcel = async function() {
    const fileInput = document.getElementById("bulkFileExcel");
    const file = fileInput.files[0];
    if (!file) return alert("⚠️ Please select an Excel file (.xlsx)!");

    const btn = document.querySelector("button[onclick='window.uploadBulkDataExcel()']")
             || document.querySelector("button[onclick='uploadBulkDataExcel()']");

    if(btn) { btn.innerHTML = "Processing..."; btn.disabled = true; }

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

        if(res.ok) {
            alert(text);
            fileInput.value = "";
            loadAdminUsers();
        } else {
            alert("❌ Upload Failed: " + text);
        }

    } catch (e) {
        alert("❌ Error: " + e.message);
    } finally {
        if(btn) { btn.innerHTML = "<i class='fas fa-upload me-1'></i> Upload .xlsx"; btn.disabled = false; }
    }
};

// =========================================================
// 6. FEEDBACK MANAGEMENT
// =========================================================

window.loadAdminFeedback = async function() {
    const tbody = document.getElementById("adminFeedbackTable");
    if(!tbody) return;

    tbody.innerHTML = "<tr><td colspan='4' class='text-center py-4'>Loading...</td></tr>";

    try {
        const feedbacks = await get('/feedback');
        tbody.innerHTML = "";

        if(!feedbacks || feedbacks.length === 0) {
            tbody.innerHTML = "<tr><td colspan='4' class='text-center py-4 text-muted'>No feedback yet.</td></tr>";
            return;
        }

        feedbacks.forEach(f => {
            const dateStr = f.createdAt ? new Date(f.createdAt).toLocaleDateString() : '-';
            tbody.innerHTML += `
                <tr>
                    <td>${f.submittedBy?.name || 'Anonymous'}</td>
                    <td class="text-warning">${"⭐".repeat(f.rating)}</td>
                    <td>${f.comments}</td>
                    <td>${dateStr}</td>
                </tr>`;
        });
    } catch(e) {
        tbody.innerHTML = "<tr><td colspan='4' class='text-danger text-center'>Error loading feedback</td></tr>";
    }
}