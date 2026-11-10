/**
 * Campus Feed / Community Wall controller (`components/wall.html`).
 */

import { get, send, getCurrentUser, parseErrorMessage } from '../utils/api.js';
import { escapeHtml, safeMediaUrl, formatTimeAgo } from '../utils/sanitize.js';

let selectedImageFile = null;
let previewObjectUrl = null;

function revokePreviewObjectUrl() {
    if (previewObjectUrl) {
        URL.revokeObjectURL(previewObjectUrl);
        previewObjectUrl = null;
    }
}

window.handleImageFileSelect = function(input) {
    if (input.files && input.files[0]) {
        selectedImageFile = input.files[0];

        const urlInput = document.getElementById("postImage");
        if (urlInput) urlInput.value = "";

        const previewImg = document.getElementById("postImagePreview");
        const previewContainer = document.getElementById("postImagePreviewContainer");
        if (previewImg && previewContainer) {
            revokePreviewObjectUrl();
            previewObjectUrl = URL.createObjectURL(selectedImageFile);
            previewImg.src = previewObjectUrl;
            previewContainer.classList.remove("d-none");
        }
    }
};

window.handleImageUrlInput = function(input) {
    const previewImg = document.getElementById("postImagePreview");
    const previewContainer = document.getElementById("postImagePreviewContainer");

    selectedImageFile = null;
    revokePreviewObjectUrl();
    const fileInput = document.getElementById("postImageFile");
    if (fileInput) fileInput.value = "";

    const validUrl = safeMediaUrl(input.value);
    if (validUrl && previewImg && previewContainer) {
        previewImg.src = validUrl;
        previewContainer.classList.remove("d-none");
    } else if (previewContainer) {
        if (previewImg) previewImg.src = "";
        previewContainer.classList.add("d-none");
    }
};

window.clearSelectedImage = function() {
    selectedImageFile = null;
    revokePreviewObjectUrl();
    const fileInput = document.getElementById("postImageFile");
    const urlInput = document.getElementById("postImage");
    const previewImg = document.getElementById("postImagePreview");
    const previewContainer = document.getElementById("postImagePreviewContainer");

    if (fileInput) fileInput.value = "";
    if (urlInput) urlInput.value = "";
    if (previewImg) previewImg.src = "";
    if (previewContainer) previewContainer.classList.add("d-none");
};

async function uploadImageFile(file) {
    const token = localStorage.getItem("jwt_token");
    const formData = new FormData();
    formData.append("file", file);

    const res = await fetch("/api/posts/upload", {
        method: "POST",
        headers: { "Authorization": `Bearer ${token}` },
        body: formData
    });

    if (!res.ok) {
        throw new Error(await parseErrorMessage(res, "Failed to upload image."));
    }

    const data = await res.json();
    return data.imageUrl;
}

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
                <strong>${isWarning ? 'Warning' : 'Content Blocked'}:</strong> ${escapeHtml(message)}
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
    const myRole = (currentUser.role || "").toUpperCase();
    const myEmail = (currentUser.email || "").toLowerCase();

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
        const authorName = p.author?.name || "Unknown User";
        const authorRole = p.author?.role || "MEMBER";
        const authorEmail = (p.author?.email || "").toLowerCase();
        const dateDisplay = p.createdAt ? formatTimeAgo(p.createdAt) : "Recently";
        const commentsCount = p.commentsCount !== undefined && p.commentsCount !== null ? p.commentsCount : 0;

        let pinnedBadge = "";
        let cardClass = "cc-feature-card cc-hover-lift";

        if (p.isPinned) {
            pinnedBadge = `<span class="badge bg-warning text-dark me-2"><i class="fas fa-thumbtack"></i> Pinned</span>`;
            cardClass = "cc-feature-card cc-hover-lift border border-warning border-2";
        }

        let actionControls = "";
        const isAdmin = myRole === "ADMIN";
        const isAuthor = Boolean(myEmail && authorEmail && myEmail === authorEmail);

        if (isAdmin) {
            const pinIcon = p.isPinned ? "fa-times-circle" : "fa-thumbtack";
            const pinTitle = p.isPinned ? "Unpin Post" : "Pin to Top";

            actionControls = `
                <div class="dropdown">
                    <button class="btn btn-sm btn-light rounded-circle" data-bs-toggle="dropdown">
                        <i class="fas fa-ellipsis-v"></i>
                    </button>
                    <ul class="dropdown-menu dropdown-menu-end shadow border-0">
                        <li><a class="dropdown-item" href="#" onclick="event.preventDefault(); window.togglePin(${p.id})">
                            <i class="fas ${pinIcon} me-2 text-primary"></i>${pinTitle}
                        </a></li>
                        <li><hr class="dropdown-divider"></li>
                        <li><a class="dropdown-item text-danger" href="#" onclick="event.preventDefault(); window.deletePost(${p.id})">
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

        const safeImg = safeMediaUrl(p.imageUrl);
        const imageHtml = safeImg ? `
            <img src="${escapeHtml(safeImg)}" class="img-fluid rounded mb-3 w-100 border cc-feed-image"
                 alt="Post attachment" onerror="this.style.display='none'">` : "";

        const card = `
        <div class="${cardClass}" id="post-card-${p.id}">
            <div class="cc-feature-card-header">
                <div class="d-flex align-items-center">
                    <div class="avatar-circle me-2 shadow-sm">
                        ${escapeHtml(authorName.charAt(0).toUpperCase())}
                    </div>
                    <div>
                        <div class="d-flex align-items-center">
                            ${pinnedBadge}
                            <h6 class="mb-0 fw-bold">${escapeHtml(authorName)}</h6>
                        </div>
                        <small class="text-muted small">
                            <span class="badge bg-light text-dark border me-1">${escapeHtml(authorRole)}</span>
                            • ${escapeHtml(dateDisplay)}
                        </small>
                    </div>
                </div>
                ${actionControls}
            </div>
            <div class="cc-feature-card-body">
                <p class="card-text mb-3 cc-feed-content">${escapeHtml(p.content)}</p>
                ${imageHtml}
                <hr class="text-muted opacity-25 my-2">
                <div class="d-flex align-items-center gap-2">
                    <button class="btn btn-light btn-sm text-primary fw-bold px-3" onclick="window.likePost(${p.id}, this)">
                        <i class="far fa-thumbs-up me-1"></i> <span>${Number(p.likes) || 0}</span> Likes
                    </button>
                    <button class="btn btn-light btn-sm text-secondary fw-bold px-3" onclick="window.toggleComments(${p.id})">
                        <i class="far fa-comment-alt me-1 text-primary"></i> <span id="comments-count-${p.id}">${Number(commentsCount) || 0}</span> Comments
                    </button>
                </div>

                <div id="comments-section-${p.id}" class="cc-comments-section d-none border-top pt-3 mt-3">
                    <div id="comments-list-${p.id}" class="cc-comments-list mb-3">
                        <div class="text-center py-2 text-muted small"><div class="spinner-border spinner-border-sm text-primary me-1"></div> Loading comments...</div>
                    </div>

                    <div class="d-flex gap-2 align-items-center">
                        <div class="cc-comment-avatar">
                            ${escapeHtml((currentUser.name || "U").charAt(0).toUpperCase())}
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

        container.insertAdjacentHTML("beforeend", card);
    });
}

window.togglePin = async function(id) {
    try {
        const res = await send(`/posts/${id}/pin`, 'POST');
        if (!res.ok) {
            alert(await parseErrorMessage(res, "Only admins can pin posts."));
            return;
        }
        await fetchPosts();
    } catch (e) {
        console.error("Pinning failed:", e);
        alert("Failed to update pin status.");
    }
};

window.createPost = async function() {
    hideWallAlert();
    const contentInput = document.getElementById("postContent");
    const imageInput = document.getElementById("postImage");
    const btn = document.getElementById("btnPost");

    if (!contentInput.value.trim()) {
        showWallAlert("Please write a message before posting!", true);
        return;
    }

    const rawUrlInput = imageInput ? imageInput.value.trim() : "";
    let finalImageUrl = "";
    if (!selectedImageFile && rawUrlInput) {
        finalImageUrl = safeMediaUrl(rawUrlInput);
        if (!finalImageUrl) {
            showWallAlert("Image URL must use http:// or https://.", true);
            return;
        }
    }

    const originalHtml = btn ? btn.innerHTML : "Publish Post";
    if (btn) {
        btn.innerHTML = `<span class="spinner-border spinner-border-sm me-1"></span> Publishing...`;
        btn.disabled = true;
    }

    try {
        if (selectedImageFile) {
            if (btn) btn.innerHTML = `<span class="spinner-border spinner-border-sm me-1"></span> Uploading photo...`;
            finalImageUrl = await uploadImageFile(selectedImageFile);
        }

        const res = await send('/posts', 'POST', {
            content: contentInput.value.trim(),
            imageUrl: finalImageUrl || null
        });

        if (!res.ok) {
            const errMsg = await parseErrorMessage(res, "Could not publish post. Check content for prohibited language.");
            showWallAlert(errMsg);
            return;
        }

        contentInput.value = "";
        window.clearSelectedImage();
        await fetchPosts();
    } catch (e) {
        console.error(e);
        showWallAlert(e.message || "An error occurred while publishing the post.");
    } finally {
        if (btn) {
            btn.innerHTML = originalHtml;
            btn.disabled = false;
        }
    }
};

window.deletePost = async function(id) {
    if (!confirm("Delete this post?")) return;
    try {
        const res = await send(`/posts/${id}`, 'DELETE');
        if (!res.ok) {
            alert(await parseErrorMessage(res, "Delete failed."));
            return;
        }
        await fetchPosts();
    } catch (e) {
        alert("Delete failed: " + e.message);
    }
};

window.likePost = async function(id, btnElement) {
    if (!btnElement || btnElement.disabled) return;
    btnElement.disabled = true;

    try {
        const res = await send(`/posts/${id}/like`, 'POST');
        if (!res.ok) {
            alert(await parseErrorMessage(res, "Could not update like."));
            return;
        }
        const data = await res.json();
        const span = btnElement.querySelector("span");
        const icon = btnElement.querySelector("i");
        if (span && typeof data.likes === "number") {
            span.innerText = String(data.likes);
        }
        if (icon && typeof data.liked === "boolean") {
            icon.classList.toggle('fas', data.liked);
            icon.classList.toggle('far', !data.liked);
        }
    } catch (e) {
        console.error("Like failed", e);
    } finally {
        btnElement.disabled = false;
    }
};

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
        const myRole = (currentUser.role || "").toUpperCase();
        const myEmail = (currentUser.email || "").toLowerCase();

        if (!comments || comments.length === 0) {
            listEl.innerHTML = `<div class="text-center py-2 text-muted small"><i class="far fa-comments me-1"></i> No comments yet. Be the first to share your thoughts!</div>`;
            return;
        }

        listEl.innerHTML = comments.map(c => {
            const authorName = c.author?.name || "Member";
            const authorRole = c.author?.role || "MEMBER";
            const authorEmail = (c.author?.email || "").toLowerCase();
            const initial = authorName.charAt(0).toUpperCase();
            const dateDisplay = c.createdAt ? formatTimeAgo(c.createdAt) : "Recently";

            const canDelete = myRole === "ADMIN" || (myEmail && myEmail === authorEmail);
            const deleteBtn = canDelete ? `
                <button class="cc-comment-delete" title="Delete comment" onclick="window.deleteComment(${postId}, ${c.id})">
                    <i class="fas fa-times"></i>
                </button>` : "";

            let repliesHtml = "";
            if (c.replies && c.replies.length > 0) {
                repliesHtml = `
                    <div class="cc-replies-container">
                        ${c.replies.map(r => {
                            const rAuthor = r.author?.name || "Member";
                            const rRole = r.author?.role || "MEMBER";
                            const rEmail = (r.author?.email || "").toLowerCase();
                            const rInitial = rAuthor.charAt(0).toUpperCase();
                            const rTime = r.createdAt ? formatTimeAgo(r.createdAt) : "Recently";
                            const canDeleteReply = myRole === "ADMIN" || (myEmail && myEmail === rEmail);
                            const rDeleteBtn = canDeleteReply ? `
                                <button class="cc-comment-delete" title="Delete reply" onclick="window.deleteComment(${postId}, ${r.id})">
                                    <i class="fas fa-times"></i>
                                </button>` : "";

                            return `
                                <div class="cc-reply-item">
                                    <div class="cc-reply-avatar">${escapeHtml(rInitial)}</div>
                                    <div class="cc-comment-body">
                                        <div class="cc-comment-header">
                                            <div>
                                                <span class="cc-comment-author">${escapeHtml(rAuthor)}</span>
                                                <span class="badge bg-light text-muted border ms-1" style="font-size:0.65rem;">${escapeHtml(rRole)}</span>
                                                <span class="cc-comment-time ms-2">${escapeHtml(rTime)}</span>
                                            </div>
                                            ${rDeleteBtn}
                                        </div>
                                        <p class="cc-comment-text">${escapeHtml(r.content)}</p>
                                    </div>
                                </div>`;
                        }).join('')}
                    </div>`;
            }

            return `
                <div class="cc-comment-item flex-column align-items-stretch" id="comment-item-${c.id}">
                    <div class="d-flex gap-2">
                        <div class="cc-comment-avatar">
                            ${escapeHtml(initial)}
                        </div>
                        <div class="cc-comment-body">
                            <div class="cc-comment-header">
                                <div>
                                    <span class="cc-comment-author">${escapeHtml(authorName)}</span>
                                    <span class="badge bg-light text-muted border ms-1" style="font-size:0.68rem;">${escapeHtml(authorRole)}</span>
                                    <span class="cc-comment-time ms-2">${escapeHtml(dateDisplay)}</span>
                                    <button class="cc-comment-reply-btn" data-comment-id="${c.id}" onclick="window.toggleReplyBox(${c.id})">
                                        <i class="fas fa-reply me-1"></i>Reply
                                    </button>
                                </div>
                                ${deleteBtn}
                            </div>
                            <p class="cc-comment-text">${escapeHtml(c.content)}</p>
                        </div>
                    </div>

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

                    ${repliesHtml}
                </div>`;
        }).join('');
    } catch (e) {
        console.error("Error loading comments:", e);
        listEl.innerHTML = `<div class="text-danger small py-1 text-center">Failed to load comments.</div>`;
    }
};

window.toggleReplyBox = function(commentId) {
    const box = document.getElementById(`reply-box-${commentId}`);
    if (!box) return;

    const isHidden = box.classList.contains("d-none");
    if (isHidden) {
        box.classList.remove("d-none");
        const input = document.getElementById(`reply-input-${commentId}`);
        if (input) input.focus();
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
        const res = await send(`/posts/${postId}/comments`, 'POST', { content, parentId });

        if (!res.ok) {
            const errMsg = await parseErrorMessage(res, "Inappropriate language detected. Your account has been flagged.");
            if (alertEl) {
                alertEl.innerHTML = `<i class="fas fa-ban me-1"></i> <strong>Blocked:</strong> ${escapeHtml(errMsg)}`;
                alertEl.classList.remove("d-none");
            }
            return;
        }

        input.value = "";
        window.toggleReplyBox(parentId);

        await window.loadComments(postId);

        const countSpan = document.getElementById(`comments-count-${postId}`);
        if (countSpan) {
            const current = parseInt(countSpan.innerText, 10) || 0;
            countSpan.innerText = String(current + 1);
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
        const res = await send(`/posts/${postId}/comments`, 'POST', { content });

        if (!res.ok) {
            const errMsg = await parseErrorMessage(res, "Inappropriate language detected. Your account has been flagged.");
            if (alertEl) {
                alertEl.innerHTML = `<i class="fas fa-ban me-1"></i> <strong>Blocked:</strong> ${escapeHtml(errMsg)}`;
                alertEl.classList.remove("d-none");
            }
            return;
        }

        input.value = "";
        await window.loadComments(postId);

        const countSpan = document.getElementById(`comments-count-${postId}`);
        if (countSpan) {
            const current = parseInt(countSpan.innerText, 10) || 0;
            countSpan.innerText = String(current + 1);
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
            alert(await parseErrorMessage(res, "Could not delete comment."));
            return;
        }

        const data = await res.json().catch(() => ({}));
        await window.loadComments(postId);

        const countSpan = document.getElementById(`comments-count-${postId}`);
        if (countSpan) {
            if (typeof data.commentsCount === "number") {
                countSpan.innerText = String(Math.max(0, data.commentsCount));
            } else {
                const current = parseInt(countSpan.innerText, 10) || 1;
                countSpan.innerText = String(Math.max(0, current - 1));
            }
        }
    } catch (e) {
        console.error("Error deleting comment:", e);
        alert("Failed to delete comment.");
    }
};
