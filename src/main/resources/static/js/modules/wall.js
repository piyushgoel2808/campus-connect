import { get, send, getCurrentUser } from '../utils/api.js';

let selectedImageFile = null;

/**
 * 1. IMAGE FILE SELECTION & PREVIEW
 */
window.handleImageFileSelect = function(input) {
    if (input.files && input.files[0]) {
        selectedImageFile = input.files[0];

        // Clear URL input
        const urlInput = document.getElementById("postImage");
        if (urlInput) urlInput.value = "";

        // Preview thumbnail
        const previewImg = document.getElementById("postImagePreview");
        const previewContainer = document.getElementById("postImagePreviewContainer");
        if (previewImg && previewContainer) {
            previewImg.src = URL.createObjectURL(selectedImageFile);
            previewContainer.classList.remove("d-none");
        }
    }
};

window.handleImageUrlInput = function(input) {
    const previewImg = document.getElementById("postImagePreview");
    const previewContainer = document.getElementById("postImagePreviewContainer");

    // Clear local file selection
    selectedImageFile = null;
    const fileInput = document.getElementById("postImageFile");
    if (fileInput) fileInput.value = "";

    if (input.value && input.value.trim().length > 5) {
        if (previewImg && previewContainer) {
            previewImg.src = input.value.trim();
            previewContainer.classList.remove("d-none");
        }
    } else {
        if (previewContainer) previewContainer.classList.add("d-none");
    }
};

window.clearSelectedImage = function() {
    selectedImageFile = null;
    const fileInput = document.getElementById("postImageFile");
    const urlInput = document.getElementById("postImage");
    const previewImg = document.getElementById("postImagePreview");
    const previewContainer = document.getElementById("postImagePreviewContainer");

    if (fileInput) fileInput.value = "";
    if (urlInput) urlInput.value = "";
    if (previewImg) previewImg.src = "";
    if (previewContainer) previewContainer.classList.add("d-none");
};

/**
 * Helper to upload image file to backend
 */
async function uploadImageFile(file) {
    const token = localStorage.getItem("jwt_token");
    const formData = new FormData();
    formData.append("file", file);

    const res = await fetch("/api/posts/upload", {
        method: "POST",
        headers: {
            "Authorization": `Bearer ${token}`
        },
        body: formData
    });

    if (!res.ok) {
        let errData;
        try {
            errData = await res.json();
        } catch(e) {
            errData = { error: "Failed to upload file." };
        }
        throw new Error(errData.error || "Failed to upload image.");
    }

    const data = await res.json();
    return data.imageUrl;
}

/**
 * Alert Banner for Moderation / Warnings
 */
function showWallAlert(message, isWarning = false) {
    const alertEl = document.getElementById("postAlert");
    if (!alertEl) return;
    alertEl.className = isWarning 
        ? "alert alert-warning mb-3 shadow-sm border-warning"
        : "alert alert-danger mb-3 shadow-sm border-danger";
    alertEl.innerHTML = `
        <div class="d-flex align-items-center justify-content-between">
            <div>
                <i class="fas ${isWarning ? 'fa-exclamation-triangle text-warning' : 'fa-ban text-danger'} me-2 fa-lg"></i>
                <strong>${isWarning ? 'Warning' : 'Content Blocked'}:</strong> ${message}
            </div>
            <button type="button" class="btn-close" onclick="this.parentElement.parentElement.classList.add('d-none')"></button>
        </div>
    `;
    alertEl.classList.remove("d-none");
    alertEl.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function hideWallAlert() {
    const alertEl = document.getElementById("postAlert");
    if (alertEl) alertEl.classList.add("d-none");
}

/**
 * 2. FETCH & RENDER FEED
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
                <p class="mb-0">Be the first to share something with the GradLink community.</p>
            </div>`;
        return;
    }

    posts.forEach(p => {
        const authorName = p.author ? p.author.name : "Unknown User";
        const authorRole = p.author ? p.author.role : "MEMBER";
        const authorEmail = p.author ? p.author.email : "";
        const dateDisplay = p.createdAt ? timeAgo(p.createdAt) : "Recently";
        const commentsCount = p.commentsCount !== undefined && p.commentsCount !== null ? p.commentsCount : 0;

        // Pinned Logic & Styling
        let pinnedBadge = "";
        let cardClass = "cc-feature-card cc-hover-lift";

        if (p.isPinned) {
            pinnedBadge = `<span class="badge bg-warning text-dark me-2"><i class="fas fa-thumbtack"></i> Pinned</span>`;
            cardClass = "cc-feature-card cc-hover-lift border border-warning border-2";
        }

        // Admin & Author Controls
        let actionControls = "";
        const isAdmin = currentUser.role === "ADMIN";
        const isAuthor = currentUser.email === authorEmail;

        if (isAdmin) {
            const pinIcon = p.isPinned ? "fa-times-circle" : "fa-thumbtack";
            const pinTitle = p.isPinned ? "Unpin Post" : "Pin to Top";

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
            actionControls = `
                <button class="btn btn-sm btn-link text-danger p-0" onclick="window.deletePost(${p.id})">
                    <i class="fas fa-trash"></i>
                </button>`;
        }

        const imageHtml = p.imageUrl ? `
            <img src="${p.imageUrl}" class="img-fluid rounded mb-3 w-100 border cc-feed-image"
                 onerror="this.style.display='none'">` : "";

        const card = `
        <div class="${cardClass}" id="post-card-${p.id}">
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
                <hr class="text-muted opacity-25 my-2">
                <div class="d-flex align-items-center gap-2">
                    <button class="btn btn-light btn-sm text-primary fw-bold px-3" onclick="window.likePost(${p.id}, this)">
                        <i class="far fa-thumbs-up me-1"></i> <span>${p.likes || 0}</span> Likes
                    </button>
                    <button class="btn btn-light btn-sm text-secondary fw-bold px-3" onclick="window.toggleComments(${p.id})">
                        <i class="far fa-comment-alt me-1 text-primary"></i> <span id="comments-count-${p.id}">${commentsCount}</span> Comments
                    </button>
                </div>

                <!-- Collapsible Comments Section -->
                <div id="comments-section-${p.id}" class="cc-comments-section d-none border-top pt-3 mt-3">
                    <div id="comments-list-${p.id}" class="cc-comments-list mb-3">
                        <div class="text-center py-2 text-muted small"><div class="spinner-border spinner-border-sm text-primary me-1"></div> Loading comments...</div>
                    </div>

                    <!-- Add Comment Box -->
                    <div class="d-flex gap-2 align-items-center">
                        <div class="cc-comment-avatar">
                            ${(currentUser.name || "U").charAt(0).toUpperCase()}
                        </div>
                        <div class="input-group input-group-sm flex-grow-1">
                            <input type="text" id="comment-input-${p.id}" class="form-control" placeholder="Write a comment..." onkeydown="if(event.key==='Enter') window.addComment(${p.id})">
                            <button class="btn btn-primary px-3" onclick="window.addComment(${p.id})">
                                <i class="fas fa-paper-plane me-1"></i> Post
                            </button>
                        </div>
                    </div>
                    <div id="comment-alert-${p.id}" class="alert alert-danger py-2 px-3 mt-2 small d-none shadow-sm"></div>
                </div>
            </div>
        </div>`;

        container.innerHTML += card;
    });
}

/**
 * 3. TOGGLE PIN FUNCTION
 */
window.togglePin = async function(id) {
    try {
        await send(`/posts/${id}/pin`, 'POST');
        fetchPosts();
    } catch(e) {
        console.error("Pinning failed:", e);
        alert("Only admins can pin posts.");
    }
};

/**
 * 4. CREATE POST WITH LOCAL IMAGE OR URL & PROFANITY HANDLING
 */
window.createPost = async function() {
    hideWallAlert();
    const contentInput = document.getElementById("postContent");
    const imageInput = document.getElementById("postImage");
    const btn = document.getElementById("btnPost");

    if (!contentInput.value.trim()) {
        showWallAlert("Please write a message before posting!", true);
        return;
    }

    let originalText = btn ? btn.innerText : "Publish Post";
    if (btn) {
        btn.innerHTML = `<span class="spinner-border spinner-border-sm me-1"></span> Publishing...`;
        btn.disabled = true;
    }

    try {
        let finalImageUrl = imageInput ? imageInput.value.trim() : "";

        // If local image file is selected, upload it first
        if (selectedImageFile) {
            btn.innerHTML = `<span class="spinner-border spinner-border-sm me-1"></span> Uploading photo...`;
            finalImageUrl = await uploadImageFile(selectedImageFile);
        }

        const res = await send('/posts', 'POST', {
            content: contentInput.value.trim(),
            imageUrl: finalImageUrl
        });

        if (!res.ok) {
            const data = await res.json();
            showWallAlert(data.error || "Could not publish post. Check content for prohibited language.");
            return;
        }

        // Reset form
        contentInput.value = "";
        window.clearSelectedImage();
        await fetchPosts();
    } catch (e) {
        console.error(e);
        showWallAlert(e.message || "An error occurred while publishing the post.");
    } finally {
        if (btn) {
            btn.innerText = originalText;
            btn.disabled = false;
        }
    }
};

/**
 * 5. DELETE POST
 */
window.deletePost = async function(id) {
    if (!confirm("Delete this post?")) return;
    try {
        const res = await send(`/posts/${id}`, 'DELETE');
        if (!res.ok) {
            alert("Delete failed.");
            return;
        }
        fetchPosts();
    } catch(e) {
        alert("Delete failed.");
    }
};

/**
 * 6. LIKE POST
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
 * 7. COMMENTS SYSTEM (TOGGLE, LOAD, ADD, DELETE)
 */
window.toggleComments = function(postId) {
    const section = document.getElementById(`comments-section-${postId}`);
    if (!section) return;

    const isHidden = section.classList.contains("d-none");
    if (isHidden) {
        section.classList.remove("d-none");
        window.loadComments(postId);
    } else {
        section.classList.add("d-none");
    }
};

window.loadComments = async function(postId) {
    const listEl = document.getElementById(`comments-list-${postId}`);
    if (!listEl) return;

    try {
        const comments = await get(`/posts/${postId}/comments`);
        const currentUser = getCurrentUser();

        if (!comments || comments.length === 0) {
            listEl.innerHTML = `<div class="text-center py-2 text-muted small"><i class="far fa-comments me-1"></i> No comments yet. Be the first to share your thoughts!</div>`;
            return;
        }

        listEl.innerHTML = comments.map(c => {
            const authorName = c.author ? c.author.name : "Member";
            const authorRole = c.author ? c.author.role : "MEMBER";
            const authorEmail = c.author ? c.author.email : "";
            const initial = authorName.charAt(0).toUpperCase();
            const dateDisplay = c.createdAt ? timeAgo(c.createdAt) : "Recently";

            const canDelete = currentUser.role === "ADMIN" || currentUser.email === authorEmail;
            const deleteBtn = canDelete ? `
                <button class="cc-comment-delete" title="Delete comment" onclick="window.deleteComment(${postId}, ${c.id})">
                    <i class="fas fa-times"></i>
                </button>` : "";

            // Render nested replies
            let repliesHtml = "";
            if (c.replies && c.replies.length > 0) {
                repliesHtml = `
                    <div class="cc-replies-container">
                        ${c.replies.map(r => {
                            const rAuthor = r.author ? r.author.name : "Member";
                            const rRole = r.author ? r.author.role : "MEMBER";
                            const rEmail = r.author ? r.author.email : "";
                            const rInitial = rAuthor.charAt(0).toUpperCase();
                            const rTime = r.createdAt ? timeAgo(r.createdAt) : "Recently";
                            const canDeleteReply = currentUser.role === "ADMIN" || currentUser.email === rEmail;
                            const rDeleteBtn = canDeleteReply ? `
                                <button class="cc-comment-delete" title="Delete reply" onclick="window.deleteComment(${postId}, ${r.id})">
                                    <i class="fas fa-times"></i>
                                </button>` : "";

                            return `
                                <div class="cc-reply-item">
                                    <div class="cc-reply-avatar">${rInitial}</div>
                                    <div class="cc-comment-body">
                                        <div class="cc-comment-header">
                                            <div>
                                                <span class="cc-comment-author">${escapeHtml(rAuthor)}</span>
                                                <span class="badge bg-light text-muted border ms-1" style="font-size:0.65rem;">${rRole}</span>
                                                <span class="cc-comment-time ms-2">${rTime}</span>
                                            </div>
                                            ${rDeleteBtn}
                                        </div>
                                        <p class="cc-comment-text">${escapeHtml(r.content)}</p>
                                    </div>
                                </div>
                            `;
                        }).join('')}
                    </div>
                `;
            }

            return `
                <div class="cc-comment-item flex-column align-items-stretch" id="comment-item-${c.id}">
                    <div class="d-flex gap-2">
                        <div class="cc-comment-avatar">
                            ${initial}
                        </div>
                        <div class="cc-comment-body">
                            <div class="cc-comment-header">
                                <div>
                                    <span class="cc-comment-author">${escapeHtml(authorName)}</span>
                                    <span class="badge bg-light text-muted border ms-1" style="font-size:0.68rem;">${authorRole}</span>
                                    <span class="cc-comment-time ms-2">${dateDisplay}</span>
                                    <button class="cc-comment-reply-btn" onclick="window.toggleReplyBox(${c.id}, '${escapeHtml(authorName)}')">
                                        <i class="fas fa-reply me-1"></i>Reply
                                    </button>
                                </div>
                                ${deleteBtn}
                            </div>
                            <p class="cc-comment-text">${escapeHtml(c.content)}</p>
                        </div>
                    </div>

                    <!-- Inline Reply Box -->
                    <div id="reply-box-${c.id}" class="cc-reply-box d-none mt-2 ms-4">
                        <div class="d-flex gap-2 align-items-center">
                            <input type="text" id="reply-input-${c.id}" class="form-control form-control-sm" placeholder="Reply to ${escapeHtml(authorName)}..." onkeydown="if(event.key==='Enter') window.addReply(${postId}, ${c.id})">
                            <button class="btn btn-primary btn-sm px-3" onclick="window.addReply(${postId}, ${c.id})">
                                <i class="fas fa-paper-plane me-1"></i> Send
                            </button>
                            <button class="btn btn-light btn-sm border" onclick="window.toggleReplyBox(${c.id})">✕</button>
                        </div>
                        <div id="reply-alert-${c.id}" class="alert alert-danger py-1 px-2 mt-1 small d-none shadow-sm"></div>
                    </div>

                    <!-- Nested Replies Container -->
                    ${repliesHtml}
                </div>`;
        }).join('');
    } catch(e) {
        console.error("Error loading comments:", e);
        listEl.innerHTML = `<div class="text-danger small py-1 text-center">Failed to load comments.</div>`;
    }
};

window.toggleReplyBox = function(commentId, authorName = "") {
    const box = document.getElementById(`reply-box-${commentId}`);
    if (!box) return;

    const isHidden = box.classList.contains("d-none");
    if (isHidden) {
        box.classList.remove("d-none");
        const input = document.getElementById(`reply-input-${commentId}`);
        if (input) {
            input.focus();
        }
    } else {
        box.classList.add("d-none");
    }
};

window.addReply = async function(postId, parentId) {
    const input = document.getElementById(`reply-input-${parentId}`);
    const alertEl = document.getElementById(`reply-alert-${parentId}`);
    if (alertEl) alertEl.classList.add("d-none");

    if (!input || !input.value.trim()) return;

    const content = input.value.trim();

    try {
        const res = await send(`/posts/${postId}/comments`, 'POST', { content: content, parentId: parentId });

        if (!res.ok) {
            const data = await res.json();
            if (alertEl) {
                alertEl.innerHTML = `<i class="fas fa-ban me-1"></i> <strong>Blocked:</strong> ${data.error || "Inappropriate language detected. Your account has been flagged."}`;
                alertEl.classList.remove("d-none");
            }
            return;
        }

        input.value = "";
        window.toggleReplyBox(parentId);

        // Refresh comments
        await window.loadComments(postId);

        // Update comment counter badge
        const countSpan = document.getElementById(`comments-count-${postId}`);
        if (countSpan) {
            const current = parseInt(countSpan.innerText) || 0;
            countSpan.innerText = current + 1;
        }
    } catch (e) {
        console.error("Reply submission failed:", e);
        if (alertEl) {
            alertEl.innerHTML = `<i class="fas fa-exclamation-circle me-1"></i> Failed to post reply.`;
            alertEl.classList.remove("d-none");
        }
    }
};

window.addComment = async function(postId) {
    const input = document.getElementById(`comment-input-${postId}`);
    const alertEl = document.getElementById(`comment-alert-${postId}`);
    if (alertEl) alertEl.classList.add("d-none");

    if (!input || !input.value.trim()) return;

    const content = input.value.trim();

    try {
        const res = await send(`/posts/${postId}/comments`, 'POST', { content: content });

        if (!res.ok) {
            const data = await res.json();
            if (alertEl) {
                alertEl.innerHTML = `<i class="fas fa-ban me-1"></i> <strong>Blocked:</strong> ${data.error || "Inappropriate language detected. Your account has been flagged."}`;
                alertEl.classList.remove("d-none");
            }
            return;
        }

        input.value = "";
        // Refresh comments
        await window.loadComments(postId);

        // Update comment counter badge
        const countSpan = document.getElementById(`comments-count-${postId}`);
        if (countSpan) {
            const current = parseInt(countSpan.innerText) || 0;
            countSpan.innerText = current + 1;
        }
    } catch (e) {
        console.error("Comment submission failed:", e);
        if (alertEl) {
            alertEl.innerHTML = `<i class="fas fa-exclamation-circle me-1"></i> Failed to post comment.`;
            alertEl.classList.remove("d-none");
        }
    }
};

window.deleteComment = async function(postId, commentId) {
    if (!confirm("Delete this comment?")) return;

    try {
        const res = await send(`/posts/comments/${commentId}`, 'DELETE');
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            alert(err.error || "Could not delete comment.");
            return;
        }

        await window.loadComments(postId);

        // Decrement comment counter badge
        const countSpan = document.getElementById(`comments-count-${postId}`);
        if (countSpan) {
            const current = parseInt(countSpan.innerText) || 1;
            countSpan.innerText = Math.max(0, current - 1);
        }
    } catch(e) {
        console.error("Error deleting comment:", e);
        alert("Failed to delete comment.");
    }
};

/**
 * HELPER: Simple HTML Escaper
 */
function escapeHtml(text) {
    if (!text) return "";
    return text
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

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