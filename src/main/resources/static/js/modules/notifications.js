/**
 * ============================================================================
 * FILE: src/main/resources/static/js/modules/notifications.js
 * PURPOSE: Top navbar Notification Bell dropdown & badge controller (`dashboard.html`).
 *
 * OVERVIEW:
 *   Manages in-app notifications for new messages, events, and job postings:
 *     1. Fetches the 10 most recent notifications (`GET /api/notifications?limit=10`)
 *        and the unread badge count (`GET /api/notifications/unread-count`).
 *     2. Listens for real-time STOMP push notifications via `onRealtimeNotification`
 *        from `chat.js` and prepends new items immediately.
 *     3. Polls `/api/notifications/unread-count` every 30 seconds as a fallback.
 *     4. Marks individual notifications read (`PUT /api/notifications/{id}/read`)
 *        and navigates to the relevant tab (`messages`, `events`, or `jobs`) when clicked.
 *     5. Supports marking all notifications read at once (`PUT /api/notifications/read-all`).
 *
 * API ENDPOINTS USED:
 *   - GET `/api/notifications?limit=10`     : Fetch recent notifications
 *   - GET `/api/notifications/unread-count` : Fetch unread count `{ unread }`
 *   - PUT `/api/notifications/{id}/read`    : Mark a single notification as read
 *   - PUT `/api/notifications/read-all`     : Mark all notifications as read
 * ============================================================================
 */

import { get, send } from '../utils/api.js';
import { onRealtimeNotification } from './chat.js';

// In-memory array of recent notification items displayed in `#notificationList`
let notifications = [];

/**
 * Formats an ISO timestamp into a compact relative time label (e.g., `just now`, `5m ago`, `2h ago`).
 *
 * @param {string} isoDate - ISO-8601 timestamp string.
 * @returns {string} Relative time string.
 */
function relativeTime(isoDate) {
    if (!isoDate) return '';
    const date = new Date(isoDate);
    const seconds = Math.floor((Date.now() - date.getTime()) / 1000);

    if (seconds < 60) return 'just now';
    if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
    if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
    return `${Math.floor(seconds / 86400)}d ago`;
}

/**
 * Returns the FontAwesome icon and color classes corresponding to a notification type.
 *
 * @param {'MESSAGE'|'EVENT'|'JOB'|string} type - Notification category.
 * @returns {string} FontAwesome icon class string.
 */
function notificationIcon(type) {
    switch (type) {
        case 'MESSAGE': return 'fa-envelope text-primary';
        case 'EVENT': return 'fa-calendar-alt text-warning';
        case 'JOB': return 'fa-briefcase text-success';
        default: return 'fa-bell text-secondary';
    }
}

/**
 * Updates the red unread count badge (`#notificationBadge`) on the navbar bell icon.
 *
 * @param {number} unread - Number of unread notifications.
 */
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

/**
 * Switches the active dashboard tab (`window.switchTab`) to match a clicked notification's type.
 *
 * @param {Object} item - Notification object containing `.type`.
 */
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

/**
 * Marks a single notification as read on the backend (`PUT /api/notifications/{id}/read`)
 * and refreshes the dropdown list and unread badge.
 *
 * @param {number} id - Notification ID.
 */
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

/**
 * Renders the current `notifications` list into `#notificationList` and binds
 * click handlers to mark items read and navigate to their target tab.
 */
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

/**
 * Fetches the latest unread notification count from `GET /api/notifications/unread-count`
 * and updates `#notificationBadge`.
 */
async function refreshUnreadCount() {
    try {
        const data = await get('/notifications/unread-count');
        updateBadge(data.unread || 0);
    } catch (e) {
        console.error('Unable to refresh unread count', e);
    }
}

/**
 * Loads the latest 10 notifications from `GET /api/notifications?limit=10`,
 * renders them, and refreshes the unread counter badge.
 */
async function loadNotifications() {
    try {
        notifications = await get('/notifications?limit=10');
        renderNotifications();
        await refreshUnreadCount();
    } catch (e) {
        console.error('Unable to load notifications', e);
    }
}

/**
 * Marks all notifications for the current user as read via `PUT /api/notifications/read-all`.
 */
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

/**
 * Initializes the notification dropdown on dashboard startup:
 * wires `#markAllNotificationsRead`, subscribes to real-time WebSocket notifications,
 * loads initial notifications, and starts a 30-second unread-count polling interval.
 */
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

