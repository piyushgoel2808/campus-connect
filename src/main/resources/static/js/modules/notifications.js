import { get, send } from '../utils/api.js';
import { onRealtimeNotification } from './chat.js';

let notifications = [];

function relativeTime(isoDate) {
    if (!isoDate) return '';
    const date = new Date(isoDate);
    const seconds = Math.floor((Date.now() - date.getTime()) / 1000);

    if (seconds < 60) return 'just now';
    if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
    if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
    return `${Math.floor(seconds / 86400)}d ago`;
}

function notificationIcon(type) {
    switch (type) {
        case 'MESSAGE': return 'fa-envelope text-primary';
        case 'EVENT': return 'fa-calendar-alt text-warning';
        case 'JOB': return 'fa-briefcase text-success';
        default: return 'fa-bell text-secondary';
    }
}

function updateBadge(unread) {
    const badge = document.getElementById('notificationBadge');
    if (!badge) return;

    if (unread > 0) {
        badge.textContent = unread > 99 ? '99+' : String(unread);
        badge.classList.remove('d-none');
    } else {
        badge.classList.add('d-none');
    }
}

function navigateForNotification(item) {
    if (!window.switchTab) return;

    if (item.type === 'MESSAGE') {
        window.switchTab('messages');
    } else if (item.type === 'EVENT') {
        window.switchTab('events');
    } else if (item.type === 'JOB') {
        window.switchTab('jobs');
    }
}

async function markAsRead(id) {
    try {
        const response = await send(`/notifications/${id}/read`, 'PUT');
        if (!response.ok) return;
        notifications = notifications.map(item => item.id === id ? { ...item, read: true } : item);
        renderNotifications();
        await refreshUnreadCount();
    } catch (e) {
        console.error('Failed to mark notification as read', e);
    }
}

function renderNotifications() {
    const list = document.getElementById('notificationList');
    if (!list) return;

    if (notifications.length === 0) {
        list.innerHTML = '<div class="small text-muted px-3 py-2">No notifications yet.</div>';
        return;
    }

    list.innerHTML = notifications.map(item => `
        <button class="dropdown-item d-flex align-items-start gap-2 py-2 ${item.read ? '' : 'fw-semibold'}" data-id="${item.id}">
            <i class="fas ${notificationIcon(item.type)} mt-1"></i>
            <div class="flex-grow-1 text-wrap">
                <div class="small">${item.title}</div>
                <div class="text-muted small">${item.body}</div>
                <div class="text-muted small">${relativeTime(item.createdAt)}</div>
            </div>
        </button>
    `).join('');

    list.querySelectorAll('button[data-id]').forEach(button => {
        button.addEventListener('click', async () => {
            const id = Number(button.dataset.id);
            const selected = notifications.find(item => item.id === id);
            if (!selected) return;

            if (!selected.read) {
                await markAsRead(id);
            }
            navigateForNotification(selected);
        });
    });
}

async function refreshUnreadCount() {
    try {
        const data = await get('/notifications/unread-count');
        updateBadge(data.unread || 0);
    } catch (e) {
        console.error('Unable to refresh unread count', e);
    }
}

async function loadNotifications() {
    try {
        notifications = await get('/notifications?limit=10');
        renderNotifications();
        await refreshUnreadCount();
    } catch (e) {
        console.error('Unable to load notifications', e);
    }
}

async function markAllRead() {
    try {
        const response = await send('/notifications/read-all', 'PUT');
        if (!response.ok) return;

        notifications = notifications.map(item => ({ ...item, read: true }));
        renderNotifications();
        updateBadge(0);
    } catch (e) {
        console.error('Unable to mark all as read', e);
    }
}

export function initNotifications() {
    const button = document.getElementById('markAllNotificationsRead');
    if (button) {
        button.addEventListener('click', markAllRead);
    }

    onRealtimeNotification((item) => {
        notifications = [item, ...notifications].slice(0, 10);
        renderNotifications();
        refreshUnreadCount();
    });

    loadNotifications();
    setInterval(refreshUnreadCount, 30000);
}
