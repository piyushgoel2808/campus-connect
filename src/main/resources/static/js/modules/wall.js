import { get, send } from '../utils/api.js';

// Load Posts
export async function fetchPosts() {
    try {
        const posts = await get('/posts');
        const container = document.getElementById("wallContainer");
        if(container) {
            if(posts.length === 0) container.innerHTML = "<p class='text-center text-muted'>No posts yet.</p>";
            else container.innerHTML = posts.map(p => `
                <div class="card mb-3 shadow-sm"><div class="card-body">
                    <div class="d-flex align-items-center mb-2">
                        <div class="avatar-circle small bg-secondary text-white rounded-circle me-2 d-flex justify-content-center align-items-center" style="width:35px;height:35px">${p.authorName.charAt(0)}</div>
                        <strong>${p.authorName}</strong>
                    </div>
                    <p class="card-text">${p.content}</p>
                    ${p.imageUrl ? `<img src="${p.imageUrl}" class="img-fluid rounded mt-2">` : ''}
                </div></div>`).join('');
        }
    } catch(e) { console.error(e); }
}

// Create Post (Exposed to window for onclick)
window.createPost = async function() {
    const content = document.getElementById("postContent").value;
    const imageUrl = document.getElementById("postImage").value;

    if(!content) return alert("Please write something.");

    try {
        await send('/posts', 'POST', { content, imageUrl });
        // Clear inputs
        document.getElementById("postContent").value = "";
        document.getElementById("postImage").value = "";
        // Refresh list
        fetchPosts();
    } catch(e) {
        alert("Failed to post: " + e.message);
    }
}