import { get, getCurrentUser } from '../utils/api.js';

// =========================================================
// CONFIGURATION & STATE
// =========================================================
const WS_URL = "/ws";
let stompClient = null;
let currentChatPartnerEmail = null;

// =========================================================
// 1. CONNECT (SECURED WITH JWT)
// =========================================================
export function connectChat() {
    if (stompClient && stompClient.connected) return;

    const socket = new SockJS(WS_URL);
    stompClient = Stomp.over(socket);
    stompClient.debug = null; // Hide STOMP frames in console for cleaner logs

    // FIX: Extract JWT to authenticate the WebSocket Handshake
    const token = localStorage.getItem("jwt_token");
    const headers = {
        'Authorization': `Bearer ${token}`
    };

    stompClient.connect(headers, function () {
        const userEmail = getCurrentUser().email;
        console.log('✅ WebSocket Connected as ' + userEmail);

        // 1. Subscribe to YOUR private queue
        stompClient.subscribe('/user/queue/messages', function (payload) {
            console.log("📩 Private Message Received");
            onPrivateMessageReceived(JSON.parse(payload.body));
        });

        // Subscribe to Typing Queue
        stompClient.subscribe('/user/queue/typing', function (payload) {
            onTypingMessageReceived(JSON.parse(payload.body));
        });

        // 2. Subscribe to Public Chat (Global Chat)
        stompClient.subscribe('/topic/public', function (payload) {
            onPublicMessageReceived(JSON.parse(payload.body));
        });

    }, function (err) {
        console.error('❌ WebSocket Error:', err);
    });
}

// =========================================================
// 2. SIDEBAR HISTORY (PRESERVED)
// =========================================================
export async function fetchRecentChats() {
    try {
        const users = await get('/messages/partners');
        const list = document.getElementById("recentChatsList");
        if(!list) return;
        list.innerHTML = "";

        users.forEach(u => {
            list.innerHTML += `
                <div class="chat-item p-3 border-bottom" style="cursor:pointer"
                     onclick="window.startDirectChat('${u.id}', '${u.name}', '${u.email}')">
                    <strong>${u.name}</strong> <br> <small>${u.role}</small>
                </div>`;
        });
    } catch(e) { console.error(e); }
}

// =========================================================
// 3. RECEIVE MESSAGE LOGIC (WITH NORMALIZATION)
// =========================================================
function onPrivateMessageReceived(message) {
    const currentUser = getCurrentUser();
    if (!currentUser) return;

    // Normalize emails to lowercase to prevent matching errors (e.g., Admin vs admin)
    const sender = message.senderName ? message.senderName.toLowerCase() : "";
    const partner = currentChatPartnerEmail ? currentChatPartnerEmail.toLowerCase() : "";
    const me = currentUser.email.toLowerCase();

    // CHECK: Is the chat modal open with the person who just messaged?
    if (currentChatPartnerEmail && (sender === partner || sender === me)) {
        const isMe = (sender === me);
        appendDirectMessage(message.content, isMe ? 'me' : 'them');
    } else {
        // Notification: Show a toast if the modal isn't open
        console.log(`🔔 New message from ${message.senderName}`);

        if(window.bootstrap && document.getElementById('liveToast')) {
            const toastBody = document.getElementById('toastBody');
            if(toastBody) {
                toastBody.innerText = `New message from ${message.senderName}`;
                const toast = new bootstrap.Toast(document.getElementById('liveToast'));
                toast.show();
            }
        }
    }
}

function appendDirectMessage(text, who) {
    const container = document.getElementById("dmMessagesArea");
    if (!container) return;

    const div = document.createElement("div");
    // Styling matches your dashboard.html alerts
    div.className = who === 'me'
        ? "alert alert-primary py-2 px-3 mb-1 ms-auto text-end"
        : "alert alert-secondary py-2 px-3 mb-1 me-auto";
    div.style.maxWidth = "75%";
    div.style.borderRadius = "15px";
    div.innerText = text;

    container.appendChild(div);
    container.scrollTop = container.scrollHeight; // Auto-scroll
}

// =========================================================
// 4. PUBLIC CHAT LOGIC (GLOBAL)
// =========================================================
function onPublicMessageReceived(message) {
    const messageArea = document.getElementById("chatArea"); // Matches Global Chat container
    if (!messageArea) return;

    const div = document.createElement('div');
    const isMe = message.senderName === getCurrentUser().email;

    div.className = `message ${isMe ? 'my-message' : 'other-message'}`;
    div.innerHTML = `<strong>${isMe ? 'Me' : message.senderName}</strong><br>${message.content}`;

    messageArea.appendChild(div);
    messageArea.scrollTop = messageArea.scrollHeight;
}

window.sendMessage = function() {
    const input = document.getElementById("messageInput");
    const content = input.value.trim();
    if(content && stompClient) {
        stompClient.send("/app/chat.sendMessage", {}, JSON.stringify({
            senderName: getCurrentUser().email,
            content: content,
            type: 'CHAT'
        }));
        input.value = '';
    }
};

// =========================================================
// 5. EXPOSED WINDOW FUNCTIONS
// =========================================================

window.startDirectChat = async function(userId, userName, userEmail) {
    currentChatPartnerEmail = userEmail;

    const titleEl = document.getElementById("dmModalTitle");
    if(titleEl) titleEl.innerText = "Chat with " + userName;

    const modalEl = document.getElementById('dmModal');
    if(modalEl) new bootstrap.Modal(modalEl).show();

    const container = document.getElementById("dmMessagesArea");
    if(container) {
        container.innerHTML = "<div class='text-center text-muted small mt-3'>Loading history...</div>";
        try {
            const messages = await get(`/messages/history?partnerEmail=${userEmail}`);
            container.innerHTML = "";

            const myEmail = getCurrentUser().email.toLowerCase();

            if (!messages || messages.length === 0) {
                container.innerHTML = "<div class='text-center text-muted small mt-5'>Say 'Hi' to start the conversation! 👋</div>";
            } else {
                messages.forEach(msg => {
                    const sender = (msg.senderName || msg.senderEmail || "").toLowerCase();
                    const isMe = sender === myEmail;
                    appendDirectMessage(msg.content, isMe ? 'me' : 'them');
                });
            }
            container.scrollTop = container.scrollHeight;
        } catch(e) {
            console.error("History Error:", e);
            container.innerHTML = "<div class='text-danger text-center'>Failed to load history.</div>";
        }
    }
};

window.sendDirectMessage = function() {
    const input = document.getElementById("dmInput");
    const content = input.value.trim();
    const user = getCurrentUser();

    if (content && currentChatPartnerEmail && stompClient) {
        const chatMessage = {
            senderName: user.email,
            receiverName: currentChatPartnerEmail,
            content: content,
            type: 'CHAT'
        };

        stompClient.send("/app/chat.private", {}, JSON.stringify(chatMessage));

        // Optimistic UI Update
        appendDirectMessage(content, 'me');
        input.value = '';
    }
};

// =========================================================
// 6. TYPING INDICATOR LOGIC
// =========================================================
let typingTimeout = null;
let isTyping = false;

window.handleTyping = function() {
    if (!currentChatPartnerEmail || !stompClient) return;

    if (!isTyping) {
        isTyping = true;
        stompClient.send("/app/chat.typing", {}, JSON.stringify({
            senderEmail: getCurrentUser().email,
            receiverEmail: currentChatPartnerEmail,
            isTyping: true
        }));
    }

    clearTimeout(typingTimeout);
    typingTimeout = setTimeout(() => {
        isTyping = false;
        stompClient.send("/app/chat.typing", {}, JSON.stringify({
            senderEmail: getCurrentUser().email,
            receiverEmail: currentChatPartnerEmail,
            isTyping: false
        }));
    }, 2000);
}

function onTypingMessageReceived(message) {
    if (currentChatPartnerEmail && message.senderEmail && message.senderEmail.toLowerCase() === currentChatPartnerEmail.toLowerCase()) {
        const indicator = document.getElementById("typingIndicator");
        if (indicator) {
            if (message.typing) { // handle both cases (getter for boolean is isTyping(), JSON might serialize as 'typing' or 'isTyping', Jackson depends on field name vs getter)
                indicator.innerText = "User is typing...";
                indicator.classList.remove("d-none");
            } else {
                indicator.classList.add("d-none");
            }
        }
    }
}

// Attach event listener once DOM is ready
document.addEventListener("DOMContentLoaded", () => {
    const input = document.getElementById("dmInput");
    if (input) {
        input.addEventListener("input", window.handleTyping);
    }
});