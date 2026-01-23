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

        tbody.innerHTML += `
            <tr onclick='window.openUserProfile(${JSON.stringify(u)})'>
                <td><div class="fw-bold">${u.name}</div></td>
                <td><span class="badge bg-primary">${u.role}</span></td>
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
    document.getElementById("modalName").innerText = user.name;
    // Populate other fields...
    new bootstrap.Modal(document.getElementById('userProfileModal')).show();
};