/**
 * ============================================================================
 * FILE: src/main/resources/static/js/modules/events.js
 * PURPOSE: Campus Events listing, RSVP toggling, and Admin Event CRUD module
 *          (`components/events.html`).
 *
 * OVERVIEW:
 *   Fetches upcoming campus events (`GET /api/events`) and renders event cards
 *   into `#eventsContainer`. Every logged-in user can toggle their RSVP status
 *   (`window.toggleRSVP`). Users with the `ADMIN` role additionally see the
 *   "+ Add Event" button (`#btnAddEvent`) and per-card controls to view the
 *   RSVP attendee list (`#participantsModal`), edit event details (`#editEventModal`),
 *   or delete an event.
 *
 * API ENDPOINTS USED:
 *   - GET    `/api/events`                    : Fetch all campus events
 *   - POST   `/api/events`                    : Create a new event (Admin)
 *   - PUT    `/api/events/{id}`               : Update an existing event (Admin)
 *   - DELETE `/api/events/{id}`               : Delete an event (Admin)
 *   - POST   `/api/events/{id}/rsvp`          : Toggle current user's RSVP
 *   - GET    `/api/events/{id}/participants`  : List users who RSVP'd (Admin)
 * ============================================================================
 */

import { get, send, getCurrentUser } from '../utils/api.js';

// In-memory cache mapping eventId -> event object for quick Edit modal population
const eventsMap = new Map();

// =========================================================
// 1. FETCH & RENDER
// =========================================================

/**
 * Fetches all events from `GET /api/events`, renders them into `#eventsContainer`,
 * and reveals `#btnAddEvent` if the current user is an `ADMIN`.
 */
export async function fetchEvents() {
    const container = document.getElementById("eventsContainer");
    if(container) container.innerHTML = '<div class="col-12 text-center py-5"><div class="spinner-border text-primary"></div></div>';

    try {
        const events = await get('/events');

        // ✅ SAFETY CHECK: Ensure 'events' is actually an array
        if (!Array.isArray(events)) {
            console.error("Expected array but got:", events);
            if(container) container.innerHTML = `<div class="alert alert-danger">Server Error: Could not load events. (Check Console)</div>`;
            return;
        }

        renderEvents(events);

        // Show "Add Event" button only for ADMIN
        const { role } = getCurrentUser();
        if (role && role.toUpperCase() === "ADMIN") {
            document.getElementById("btnAddEvent")?.classList.remove("d-none");
        }
    } catch (e) {
        console.error("Fetch Events Failed:", e);
        if(container) container.innerHTML = `<div class="alert alert-danger">Network Error: ${e.message}</div>`;
    }
}

/**
 * Renders the list of event cards inside `#eventsContainer`.
 *
 * @param {Array<Object>} events - Array of event objects from `/api/events`.
 */
function renderEvents(events) {
    const container = document.getElementById("eventsContainer");
    if(!container) return;
    container.innerHTML = "";
    eventsMap.clear();

    const { role } = getCurrentUser();
    const isAdmin = role && role.toUpperCase() === "ADMIN";

    if (events.length === 0) {
        container.innerHTML = `
            <div class="col-12">
                <div class="cc-empty-state cc-panel">
                    <i class="fas fa-calendar-alt fa-lg"></i>
                    <h5 class="mb-2">No upcoming events</h5>
                    <p class="mb-0">New events will appear here when admins post them.</p>
                </div>
            </div>`;
        return;
    }

    events.forEach(e => {
        eventsMap.set(e.id, e);
        const dateStr = e.dateTime ? new Date(e.dateTime).toDateString() : "TBD";
        const timeStr = e.dateTime ? new Date(e.dateTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : "";

        // 1. Participant Count Badge
        const count = e.participantCount || 0;
        const countBadge = `<span class="badge rounded-pill text-bg-light border mb-2"><i class="fas fa-user-check me-1"></i> ${count} Going</span>`;

        // 2. Admin Controls (View RSVPs, Edit, Delete)
        let adminControls = "";
        if (isAdmin) {
            adminControls = `
                <div class="mt-3 pt-2 border-top d-flex justify-content-between">
                    <div>
                        <button class="btn btn-sm btn-info text-white me-1" onclick="window.viewParticipants(${e.id})" title="View RSVPs">
                            <i class="fas fa-users"></i>
                        </button>
                        <button class="btn btn-sm btn-warning" onclick="window.openEditEvent(${e.id})" title="Edit">
                            <i class="fas fa-edit"></i>
                        </button>
                    </div>
                    <button class="btn btn-sm btn-danger" onclick="window.deleteEvent(${e.id})" title="Delete">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>`;
        }

        // 3. RSVP Button (reflects whether the current user is already attending)
        let rsvpBtn = e.attending
            ? `<button class="btn btn-success w-100" onclick="window.toggleRSVP(${e.id})"><i class="fas fa-check me-1"></i> Going</button>`
            : `<button class="btn btn-outline-primary w-100" onclick="window.toggleRSVP(${e.id})">RSVP</button>`;

        // 4. Build Card
        container.innerHTML += `
            <div class="col-md-4 mb-4">
                <article class="cc-feature-card cc-hover-lift h-100 d-flex flex-column">
                    <div class="cc-feature-card-header">
                        <div>
                            <h5 class="cc-feature-title mb-1">${e.title || 'Untitled Event'}</h5>
                            <div class="cc-feature-meta"><i class="fas fa-map-marker-alt me-1"></i> ${e.location || 'TBA'}</div>
                        </div>
                        <div class="text-end">
                            <span class="cc-pill">${dateStr}</span>
                            <div class="small text-muted mt-2">${timeStr}</div>
                        </div>
                    </div>
                    <div class="cc-feature-card-body d-flex flex-column flex-grow-1">
                        <p class="cc-feature-meta flex-grow-1">${e.description || ''}</p>
                        <div>${countBadge}</div>

                        <div class="mt-3">${rsvpBtn}</div>
                        ${adminControls}
                    </div>
                </article>
            </div>`;
    });
}

// =========================================================
// 2. EXPOSED FUNCTIONS (Attached to Window)
// =========================================================

/**
 * A. Create New Event
 * Reads inputs from `#eventModal`, sends `POST /api/events`, closes the modal,
 * resets the form, and reloads the events grid.
 */
window.publishNewEvent = async function() {
    const rawDate = document.getElementById("evtDate")?.value || "";
    const cleanDate = rawDate ? rawDate.slice(0, 16) : null;

    const data = {
        title: document.getElementById("evtTitle")?.value.trim() || "",
        description: document.getElementById("evtDesc")?.value.trim() || "",
        location: document.getElementById("evtLoc")?.value.trim() || "",
        dateTime: cleanDate
    };

    if(!data.title || !data.dateTime) return alert("Title and Date are required");

    try {
        await send('/events', 'POST', data);

        alert("✅ Event Created!");

        // Close Modal
        const modalEl = document.getElementById('eventModal');
        if (modalEl) {
            bootstrap.Modal.getOrCreateInstance(modalEl).hide();
        }

        // Reset Form
        if (document.getElementById("evtTitle")) document.getElementById("evtTitle").value = "";
        if (document.getElementById("evtDate")) document.getElementById("evtDate").value = "";
        if (document.getElementById("evtLoc")) document.getElementById("evtLoc").value = "";
        if (document.getElementById("evtDesc")) document.getElementById("evtDesc").value = "";

        fetchEvents();

    } catch(e) {
        console.error(e);
        alert("Error creating event: " + e.message);
    }
};

/**
 * B. View Participants (Fetch List)
 * Opens `#participantsModal` and loads the list of users who have RSVP'd to
 * the specified event (`GET /api/events/{eventId}/participants`).
 *
 * @param {number} eventId - Target event ID.
 */
window.viewParticipants = async function(eventId) {
    const list = document.getElementById("participantsList");
    if(!list) return;

    list.innerHTML = "<li class='list-group-item text-center'>Loading...</li>";

    // Open Modal
    const modalEl = document.getElementById('participantsModal');
    if (modalEl) {
        bootstrap.Modal.getOrCreateInstance(modalEl).show();
    }

    try {
        const users = await get(`/events/${eventId}/participants`);
        list.innerHTML = "";

        if (!Array.isArray(users) || users.length === 0) {
            list.innerHTML = "<li class='list-group-item text-center text-muted'>No one has RSVP'd yet.</li>";
            return;
        }

        users.forEach(u => {
            const badgeClass = u.role === 'ALUMNI' ? 'bg-warning text-dark' : 'bg-secondary';
            list.innerHTML += `
                <li class="list-group-item d-flex justify-content-between align-items-center">
                    <div>
                        <strong>${u.name}</strong> <br>
                        <small class="text-muted">${u.email}</small>
                    </div>
                    <span class="badge ${badgeClass}">${u.role}</span>
                </li>`;
        });
    } catch (e) {
        console.error(e);
        list.innerHTML = "<li class='list-group-item text-danger'>Failed to load list.</li>";
    }
};

/**
 * C. Toggle RSVP
 * Toggles the current user's attendance for an event via `POST /api/events/{id}/rsvp`.
 *
 * @param {number} id - Target event ID.
 */
window.toggleRSVP = async function(id) {
    try {
        await send(`/events/${id}/rsvp`, 'POST');
        fetchEvents();
    } catch(e) { console.error(e); }
};

/**
 * D. Delete Event
 * Deletes an event via `DELETE /api/events/{id}` after confirmation.
 *
 * @param {number} id - Target event ID.
 */
window.deleteEvent = async function(id) {
    if(confirm("Are you sure you want to delete this event?")) {
        await send(`/events/${id}`, 'DELETE');
        fetchEvents();
    }
};

/**
 * E. Edit Event (Open Modal)
 * Populates `#editEventModal` with existing event details from `eventsMap` and shows it.
 *
 * @param {number|Object|string} eventOrId - Event ID, event object, or URL-encoded JSON string.
 */
window.openEditEvent = function(eventOrId) {
    const event = (typeof eventOrId === 'object' && eventOrId !== null)
        ? eventOrId
        : (typeof eventOrId === 'string' && eventOrId.startsWith('%7B')
            ? JSON.parse(decodeURIComponent(eventOrId))
            : eventsMap.get(Number(eventOrId)));
    if (!event) return;

    document.getElementById("editEventId").value = event.id;
    document.getElementById("editEventTitle").value = event.title || "";
    document.getElementById("editEventDesc").value = event.description || "";
    document.getElementById("editEventLoc").value = event.location || "";

    if(event.dateTime) {
        const raw = String(event.dateTime);
        document.getElementById("editEventDate").value = raw.length >= 16 ? raw.slice(0, 16) : raw;
    }

    const modalEl = document.getElementById('editEventModal');
    if (modalEl) {
        bootstrap.Modal.getOrCreateInstance(modalEl).show();
    }
};

/**
 * F. Submit Edit
 * Reads modified fields from `#editEventModal` and sends `PUT /api/events/{id}`.
 */
window.submitEditEvent = async function() {
    const id = document.getElementById("editEventId").value;
    const rawDate = document.getElementById("editEventDate").value;
    const cleanDate = rawDate ? rawDate.slice(0, 16) : null;

    const title = document.getElementById("editEventTitle").value.trim();
    if (!title || !cleanDate) {
        alert("Please provide at least a Title and a Date.");
        return;
    }

    const data = {
        title: title,
        description: document.getElementById("editEventDesc").value.trim(),
        location: document.getElementById("editEventLoc").value.trim(),
        dateTime: cleanDate
    };

    try {
        await send(`/events/${id}`, 'PUT', data);

        alert("✅ Event Updated!");
        const modalEl = document.getElementById('editEventModal');
        if (modalEl) {
            bootstrap.Modal.getOrCreateInstance(modalEl).hide();
        }
        fetchEvents();

    } catch (e) {
        console.error(e);
        alert("Failed to update event: " + e.message);
    }
};