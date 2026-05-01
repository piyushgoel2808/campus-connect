import { get, send, getCurrentUser } from '../utils/api.js';

/**
 * 1. FETCH & RENDER FEED
 */
export async function fetchPosts() {
    const container = document.getElementById("postsContainer");
    if (!container) return;

    container.innerHTML = `
        <div class="cc-empty-state cc-panel">
            <div class="spinner-border text-primary" role="status"></div>
            <p class="mt-2 text-muted">Loading your feed...</p>
        </div>`;

    try {
        const posts = await get('/posts');
        renderPosts(posts);
    } catch (e) {
        console.error("Feed Error:", e);
        container.innerHTML = `<div class="alert alert-danger text-center">⚠️ Failed to load feed.</div>`;
    }
}

function renderPosts(posts) {
    const container = document.getElementById("postsContainer");
    container.innerHTML = "";
    const currentUser = getCurrentUser();

    if (!posts || posts.length === 0) {
        container.innerHTML = `
            <div class="cc-empty-state cc-panel">
                <i class="fas fa-newspaper fa-lg"></i>
                <h5 class="mb-2">No posts yet</h5>
                <p class="mb-0">Be the first to share something with the campus.</p>
            </div>`;
        return;
    }

    posts.forEach(p => {
        const authorName = p.author ? p.author.name : "Unknown User";
        const authorRole = p.author ? p.author.role : "MEMBER";
        const authorEmail = p.author ? p.author.email : "";
        const dateDisplay = p.createdAt ? timeAgo(p.createdAt) : "Recently";

        // 1. PINNED LOGIC & STYLING
        let pinnedBadge = "";
        let cardClass = "cc-feature-card cc-hover-lift";

        if (p.isPinned) {
            pinnedBadge = `<span class="badge bg-warning text-dark me-2"><i class="fas fa-thumbtack"></i> Pinned</span>`;
            cardClass = "cc-feature-card cc-hover-lift border border-warning border-2";
        }

        // 2. ADMIN & AUTHOR CONTROLS
        let actionControls = "";
        const isAdmin = currentUser.role === "ADMIN";
        const isAuthor = currentUser.email === authorEmail;

        if (isAdmin) {
            const pinIcon = p.isPinned ? "fa-times-circle" : "fa-thumbtack";
            const pinTitle = p.isPinned ? "Unpin Post" : "Pin to Top";

            // Admins get a dropdown for Pin/Delete
            actionControls = `
                <div class="dropdown">
                    <button class="btn btn-sm btn-light rounded-circle" data-bs-toggle="dropdown">
                        <i class="fas fa-ellipsis-v"></i>
                    </button>
                    <ul class="dropdown-menu dropdown-menu-end shadow border-0">
                        <li><a class="dropdown-item" href="#" onclick="window.togglePin(${p.id})">
                            <i class="fas ${pinIcon} me-2 text-primary"></i>${pinTitle}
                        </a></li>
                        <li><hr class="dropdown-divider"></li>
                        <li><a class="dropdown-item text-danger" href="#" onclick="window.deletePost(${p.id})">
                            <i class="fas fa-trash me-2"></i>Delete Post
                        </a></li>
                    </ul>
                </div>`;
        } else if (isAuthor) {
            // Authors just get a simple delete button
            actionControls = `
                <button class="btn btn-sm btn-link text-danger p-0" onclick="window.deletePost(${p.id})">
                    <i class="fas fa-trash"></i>
                </button>`;
        }

        const imageHtml = p.imageUrl ? `
            <img src="${p.imageUrl}" class="img-fluid rounded mb-3 w-100 border cc-feed-image"
                 onerror="this.style.display='none'">` : "";

        const card = `
        <div class="${cardClass}">
            <div class="cc-feature-card-header">
                <div class="d-flex align-items-center">
                    <div class="avatar-circle me-2 shadow-sm">
                        ${authorName.charAt(0).toUpperCase()}
                    </div>
                    <div>
                        <div class="d-flex align-items-center">
                            ${pinnedBadge}
                            <h6 class="mb-0 fw-bold">${authorName}</h6>
                        </div>
                        <small class="text-muted small">
                            <span class="badge bg-light text-dark border me-1">${authorRole}</span>
                            • ${dateDisplay}
                        </small>
                    </div>
                </div>
                ${actionControls}
            </div>
            <div class="cc-feature-card-body">
                <p class="card-text mb-3 cc-feed-content">${p.content}</p>
                ${imageHtml}
                <hr class="text-muted opacity-25">
                <div class="d-flex align-items-center">
                    <button class="btn btn-light btn-sm text-primary fw-bold px-3" onclick="window.likePost(${p.id}, this)">
                        <i class="far fa-thumbs-up me-1"></i> <span>${p.likes || 0}</span> Likes
                    </button>
                </div>
            </div>
        </div>`;

        container.innerHTML += card;
    });
}

/**
 * NEW: TOGGLE PIN FUNCTION
 */
window.togglePin = async function(id) {
    try {
        const res = await send(`/posts/${id}/pin`, 'POST');
        // Refresh feed so sorting (pinned first) is applied immediately
        fetchPosts();
    } catch(e) {
        console.error("Pinning failed:", e);
        alert("Only admins can pin posts.");
    }
};

/**
 * 2. CREATE POST
 */
window.createPost = async function() {
    const contentInput = document.getElementById("postContent");
    const imageInput = document.getElementById("postImage");
    const btn = document.getElementById("btnPost");

    if (!contentInput.value.trim()) return alert("Please write a message!");

    let originalText = btn ? btn.innerText : "Post";
    if (btn) {
        btn.innerText = "Posting...";
        btn.disabled = true;
    }

    try {
        await send('/posts', 'POST', {
            content: contentInput.value,
            imageUrl: imageInput.value
        });

        contentInput.value = "";
        imageInput.value = "";
        await fetchPosts();
    } catch (e) {
        console.error(e);
        alert("Could not publish post.");
    } finally {
        if (btn) {
            btn.innerText = originalText;
            btn.disabled = false;
        }
    }
};

/**
 * 3. DELETE POST
 */
window.deletePost = async function(id) {
    if (!confirm("Delete this post?")) return;
    try {
        await send(`/posts/${id}`, 'DELETE');
        fetchPosts();
    } catch(e) {
        alert("Delete failed.");
    }
};

/**
 * 4. LIKE POST
 */
window.likePost = async function(id, btnElement) {
    try {
        const span = btnElement.querySelector("span");
        const icon = btnElement.querySelector("i");

        let currentLikes = parseInt(span.innerText);
        span.innerText = currentLikes + 1;
        icon.classList.replace('far', 'fas');
        btnElement.classList.add('disabled');

        await send(`/posts/${id}/like`, 'POST');
    } catch(e) {
        console.error("Like failed", e);
    }
};

/**
 * HELPER: Time Ago Logic
 */
function timeAgo(dateString) {
    const date = new Date(dateString);
    const now = new Date();
    const seconds = Math.floor((now - date) / 1000);

    let interval = Math.floor(seconds / 31536000);
    if (interval >= 1) return interval + "y ago";

    interval = Math.floor(seconds / 2592000);
    if (interval >= 1) return interval + "mo ago";

    interval = Math.floor(seconds / 86400);
    if (interval >= 1) return interval + "d ago";

    interval = Math.floor(seconds / 3600);
    if (interval >= 1) return interval + "h ago";

    interval = Math.floor(seconds / 60);
    if (interval >= 1) return interval + "m ago";

    return seconds < 10 ? "Just now" : Math.floor(seconds) + "s ago";
}