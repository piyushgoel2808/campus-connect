/**
 * Real-time STOMP over SockJS WebSocket client for Global Chat, 1-on-1 Direct Messaging,
 * Typing Indicators, and Push Notifications.
 */

import { get, getCurrentUser } from '../utils/api.js';
import { escapeHtml } from '../utils/sanitize.js';

const WS_URL = "/ws";
let stompClient = null;
let currentChatPartnerEmail = null;
const notificationListeners = [];
let typingTimeout = null;
let isTyping = false;
let reconnectTimer = null;
let isExplicitDisconnect = false;

if (typeof document !== 'undefined') {
    document.addEventListener("visibilitychange", () => {
        if (document.visibilityState === "visible") {
            if (!isExplicitDisconnect && (!stompClient || !stompClient.connected)) {
                connectChat();
            }
        }
    });
    window.addEventListener("online", () => {
        if (!isExplicitDisconnect && (!stompClient || !stompClient.connected)) {
            connectChat();
        }
    });
}

export function onRealtimeNotification(handler) {
    if (typeof handler === 'function') {
        notificationListeners.push(handler);
    }
}

export function connectChat() {
    isExplicitDisconnect = false;
    if (stompClient && stompClient.connected) return;

    const token = localStorage.getItem("jwt_token");
    if (!token) return;

    if (reconnectTimer) {
        clearTimeout(reconnectTimer);
        reconnectTimer = null;
    }

    try {
        const socket = new SockJS(WS_URL);
        stompClient = Stomp.over(socket);
        stompClient.debug = null;

        const headers = {
            'Authorization': `Bearer ${token}`
        };

        stompClient.connect(headers, function () {
            const statusBadge = document.getElementById("chatStatus");
            if (statusBadge) {
                statusBadge.className = "badge bg-success";
                statusBadge.innerHTML = `<i class="fas fa-circle me-1" style="font-size: 0.55rem;"></i>Online`;
            }

            const chatArea = document.getElementById("chatArea");
            if (chatArea && !chatArea.hasChildNodes()) {
                chatArea.innerHTML = `
                    <div class="chat-placeholder text-center text-muted small py-4 my-auto">
                        <i class="fas fa-comments text-primary fa-2x mb-2 d-block" style="opacity: 0.45;"></i>
                        <strong>Welcome to Global Chat!</strong><br>
                        Messages sent here are visible to the entire GradLink community.
                    </div>`;
            }

            stompClient.subscribe('/user/queue/messages', function (payload) {
                onPrivateMessageReceived(JSON.parse(payload.body));
            });

            stompClient.subscribe('/user/queue/typing', function (payload) {
                onTypingMessageReceived(JSON.parse(payload.body));
            });

            stompClient.subscribe('/user/queue/notifications', function (payload) {
                const notification = JSON.parse(payload.body);
                notificationListeners.forEach(listener => {
                    try {
                        listener(notification);
                    } catch (e) {
                        console.error('Notification listener error:', e);
                    }
                });
            });

            stompClient.subscribe('/topic/public', function (payload) {
                onPublicMessageReceived(JSON.parse(payload.body));
            });
        }, function (err) {
            console.warn('WebSocket connection lost or failed:', err);
            const statusBadge = document.getElementById("chatStatus");
            if (statusBadge) {
                statusBadge.className = "badge bg-secondary";
                statusBadge.innerHTML = `<i class="fas fa-sync fa-spin me-1" style="font-size: 0.55rem;"></i>Reconnecting...`;
            }
            stompClient = null;
            if (!isExplicitDisconnect && !reconnectTimer) {
                reconnectTimer = setTimeout(() => {
                    reconnectTimer = null;
                    connectChat();
                }, 3000);
            }
        });
    } catch (e) {
        console.error('Failed creating WebSocket:', e);
    }
}

export function disconnectChat() {
    isExplicitDisconnect = true;
    if (reconnectTimer) {
        clearTimeout(reconnectTimer);
        reconnectTimer = null;
    }
    clearTimeout(typingTimeout);
    isTyping = false;
    currentChatPartnerEmail = null;
    if (stompClient) {
        try {
            if (stompClient.connected) {
                stompClient.disconnect(() => {});
            }
        } catch (_) {}
        stompClient = null;
    }
}

export async function fetchRecentChats() {
    try {
        const users = await get('/messages/partners');
        const list = document.getElementById("recentChatsList");
        if (!list) return;
        list.innerHTML = "";

        if (!Array.isArray(users) || users.length === 0) {
            list.innerHTML = `
                <div class="cc-empty-state">
                    <i class="fas fa-comments"></i>
                    <h5 class="mb-2">No conversations yet</h5>
                    <p class="mb-0">Find peers or alumni in the Directory tab to start a 1-on-1 chat.</p>
                </div>`;
            return;
        }

        users.forEach(u => {
            const item = document.createElement("div");
            item.className = "chat-item p-3 border-bottom";
            item.style.cursor = "pointer";
            item.innerHTML = `<strong>${escapeHtml(u.name || "Member")}</strong><br><small class="text-muted">${escapeHtml(u.role || "")}</small>`;
            item.addEventListener("click", () => window.startDirectChat(u.id, u.name || "Member", u.email));
            list.appendChild(item);
        });
    } catch (e) {
        console.error(e);
    }
}

function onPrivateMessageReceived(message) {
    const currentUser = getCurrentUser();
    if (!currentUser || !currentUser.email) return;

    const sender = message.senderName ? message.senderName.toLowerCase() : "";
    const receiver = message.receiverName ? message.receiverName.toLowerCase() : "";
    const partner = currentChatPartnerEmail ? currentChatPartnerEmail.toLowerCase() : "";
    const me = currentUser.email.toLowerCase();

    if (currentChatPartnerEmail && (sender === partner || (sender === me && receiver === partner))) {
        const isMe = (sender === me);
        appendDirectMessage(message.content, isMe ? 'me' : 'them');
    } else if (sender !== me) {
        if (window.bootstrap && document.getElementById('liveToast')) {
            const toastBody = document.getElementById('toastBody');
            if (toastBody) {
                toastBody.innerText = `New message from ${message.senderName}`;
                const toast = bootstrap.Toast.getOrCreateInstance(document.getElementById('liveToast'));
                toast.show();
            }
        }
    }
}

function appendDirectMessage(text, who) {
    const container = document.getElementById("dmMessagesArea");
    if (!container) return;

    const emptyHint = container.querySelector(".dm-empty-hint");
    if (emptyHint) emptyHint.remove();

    const div = document.createElement("div");
    div.className = who === 'me'
        ? "alert alert-primary py-2 px-3 mb-1 ms-auto text-end"
        : "alert alert-secondary py-2 px-3 mb-1 me-auto";
    div.style.maxWidth = "75%";
    div.style.borderRadius = "15px";
    div.innerText = text || "";

    container.appendChild(div);
    container.scrollTop = container.scrollHeight;
}

function onPublicMessageReceived(message) {
    const messageArea = document.getElementById("chatArea");
    if (!messageArea) return;

    const placeholder = messageArea.querySelector(".chat-placeholder");
    if (placeholder) placeholder.remove();

    const div = document.createElement('div');
    const currentUser = getCurrentUser();
    const senderLower = (message.senderName || "").toLowerCase();
    const isMe = Boolean(
        (currentUser.email && senderLower === currentUser.email.toLowerCase()) ||
        (currentUser.name && message.senderName === currentUser.name)
    );

    div.className = `message ${isMe ? 'my-message' : 'other-message'}`;
    const displayName = isMe ? 'You' : (message.senderName || 'Member');
    div.innerHTML = `<strong class="small d-block mb-1" style="opacity: 0.85; font-size: 0.78rem;">${escapeHtml(displayName)}</strong><div>${escapeHtml(message.content || "")}</div>`;

    messageArea.appendChild(div);
    messageArea.scrollTop = messageArea.scrollHeight;
}

window.sendMessage = function() {
    const input = document.getElementById("messageInput");
    if (!input) return;
    const content = input.value.trim();
    if (!content) return;

    if (!stompClient || !stompClient.connected) {
        connectChat();
        alert("Connecting to Global Chat... Please wait 2 seconds and click send again.");
        return;
    }

    const currentUser = getCurrentUser();
    stompClient.send("/app/chat.sendMessage", {}, JSON.stringify({
        senderName: currentUser.name || currentUser.email || "Community Member",
        content: content,
        type: 'CHAT'
    }));
    input.value = '';
};

window.startDirectChat = async function(userId, userName, userEmail) {
    if (!userEmail) return;
    currentChatPartnerEmail = userEmail;

    const titleEl = document.getElementById("dmModalTitle");
    if (titleEl) titleEl.innerText = "Chat with " + (userName || userEmail);

    const indicator = document.getElementById("typingIndicator");
    if (indicator) indicator.classList.add("d-none");

    const modalEl = document.getElementById('dmModal');
    if (modalEl) bootstrap.Modal.getOrCreateInstance(modalEl).show();

    const container = document.getElementById("dmMessagesArea");
    if (container) {
        container.innerHTML = "<div class='text-center text-muted small mt-3'>Loading history...</div>";
        try {
            const messages = await get(`/messages/history?partnerEmail=${encodeURIComponent(userEmail)}`);
            container.innerHTML = "";

            const myEmail = (getCurrentUser().email || "").toLowerCase();

            if (!messages || messages.length === 0) {
                container.innerHTML = "<div class='dm-empty-hint text-center text-muted small mt-5'>Say 'Hi' to start the conversation! 👋</div>";
            } else {
                messages.forEach(msg => {
                    const sender = (msg.senderName || msg.senderEmail || "").toLowerCase();
                    const isMe = sender === myEmail;
                    appendDirectMessage(msg.content, isMe ? 'me' : 'them');
                });
            }
            container.scrollTop = container.scrollHeight;
        } catch (e) {
            console.error("History Error:", e);
            container.innerHTML = "<div class='text-danger text-center'>Failed to load history.</div>";
        }
    }
};

window.sendDirectMessage = function() {
    const input = document.getElementById("dmInput");
    if (!input) return;
    const content = input.value.trim();
    if (!content || !currentChatPartnerEmail) return;

    if (!stompClient || !stompClient.connected) {
        alert("Messaging connection is offline. Please wait for reconnection.");
        return;
    }

    const user = getCurrentUser();
    stompClient.send("/app/chat.private", {}, JSON.stringify({
        senderName: user.email,
        receiverName: currentChatPartnerEmail,
        content: content,
        type: 'CHAT'
    }));

    input.value = '';
    if (isTyping) {
        clearTimeout(typingTimeout);
        isTyping = false;
        stompClient.send("/app/chat.typing", {}, JSON.stringify({
            senderEmail: user.email,
            receiverEmail: currentChatPartnerEmail,
            isTyping: false
        }));
    }
};

window.handleTyping = function() {
    if (!currentChatPartnerEmail || !stompClient || !stompClient.connected) return;

    const myEmail = getCurrentUser().email;
    if (!isTyping) {
        isTyping = true;
        stompClient.send("/app/chat.typing", {}, JSON.stringify({
            senderEmail: myEmail,
            receiverEmail: currentChatPartnerEmail,
            isTyping: true
        }));
    }

    clearTimeout(typingTimeout);
    typingTimeout = setTimeout(() => {
        isTyping = false;
        if (stompClient && stompClient.connected && currentChatPartnerEmail) {
            stompClient.send("/app/chat.typing", {}, JSON.stringify({
                senderEmail: myEmail,
                receiverEmail: currentChatPartnerEmail,
                isTyping: false
            }));
        }
    }, 2000);
};

function onTypingMessageReceived(message) {
    if (currentChatPartnerEmail && message.senderEmail &&
            message.senderEmail.toLowerCase() === currentChatPartnerEmail.toLowerCase()) {
        const indicator = document.getElementById("typingIndicator");
        if (indicator) {
            const active = Boolean(message.isTyping ?? message.typing);
            if (active) {
                indicator.innerText = "User is typing...";
                indicator.classList.remove("d-none");
            } else {
                indicator.classList.add("d-none");
            }
        }
    }
}

document.addEventListener("DOMContentLoaded", () => {
    const input = document.getElementById("dmInput");
    if (input) {
        input.addEventListener("input", window.handleTyping);
    }
});
