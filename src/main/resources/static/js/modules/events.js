import { get, send, getCurrentUser } from '../utils/api.js';

// =========================================================
// 1. FETCH & RENDER
// =========================================================

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
        if (role === "ADMIN") {
            document.getElementById("btnAddEvent")?.classList.remove("d-none");
        }
    } catch (e) {
        console.error("Fetch Events Failed:", e);
        if(container) container.innerHTML = `<div class="alert alert-danger">Network Error: ${e.message}</div>`;
    }
}

function renderEvents(events) {
    const container = document.getElementById("eventsContainer");
    if(!container) return;
    container.innerHTML = "";

    const { role } = getCurrentUser();

    if (events.length === 0) {
        container.innerHTML = "<div class='col-12 text-center text-muted'>No upcoming events found.</div>";
        return;
    }

    events.forEach(e => {
        const dateStr = new Date(e.dateTime).toDateString();

        // 1. Participant Count Badge
        const count = e.participantCount || 0;
        const countBadge = `<span class="badge bg-secondary mb-2"><i class="fas fa-user-check"></i> ${count} Going</span>`;

        // 2. Admin Controls
        let adminControls = "";
        if (role === "ADMIN") {
            const safeEvent = encodeURIComponent(JSON.stringify(e));
            adminControls = `
                <div class="mt-3 pt-2 border-top d-flex justify-content-between">
                    <div>
                        <button class="btn btn-sm btn-info text-white me-1" onclick="window.viewParticipants(${e.id})" title="View RSVPs">
                            <i class="fas fa-users"></i>
                        </button>
                        <button class="btn btn-sm btn-warning" onclick="window.openEditEvent('${safeEvent}')" title="Edit">
                            <i class="fas fa-edit"></i>
                        </button>
                    </div>
                    <button class="btn btn-sm btn-danger" onclick="window.deleteEvent(${e.id})" title="Delete">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>`;
        }

        // 3. RSVP Button
        let rsvpBtn = e.attending
            ? `<button class="btn btn-success w-100" onclick="window.toggleRSVP(${e.id})"><i class="fas fa-check"></i> Going</button>`
            : `<button class="btn btn-outline-primary w-100" onclick="window.toggleRSVP(${e.id})">RSVP</button>`;

        // 4. Build Card
        container.innerHTML += `
            <div class="col-md-4 mb-4">
                <div class="card h-100 shadow-sm">
                    <div class="card-body d-flex flex-column">
                        <div class="d-flex justify-content-between mb-2">
                            <h5 class="card-title text-primary mb-0">${e.title}</h5>
                            <small class="text-muted">${dateStr}</small>
                        </div>
                        <h6 class="text-muted small mb-3"><i class="fas fa-map-marker-alt"></i> ${e.location}</h6>
                        <p class="card-text flex-grow-1">${e.description}</p>

                        <div>${countBadge}</div>

                        <div class="mt-auto pt-2">${rsvpBtn}</div>
                        ${adminControls}
                    </div>
                </div>
            </div>`;
    });
}

// =========================================================
// 2. EXPOSED FUNCTIONS (Attached to Window)
// =========================================================

// A. Create New Event (UPDATED: Clean Date Logic)
window.publishNewEvent = async function() {
    const rawDate = document.getElementById("evtDate").value; // e.g., "2026-01-23T14:30:00"

    // Safety: Remove seconds if they exist, so it matches "yyyy-MM-dd'T'HH:mm"
    const cleanDate = rawDate ? rawDate.slice(0, 16) : null;

    const data = {
        title: document.getElementById("evtTitle").value,
        description: document.getElementById("evtDesc").value,
        location: document.getElementById("evtLoc").value,
        dateTime: cleanDate // Send the clean version
    };

    if(!data.title || !data.dateTime) return alert("Title and Date are required");

    try {
        await send('/events', 'POST', data);

        alert("✅ Event Created!");

        // Close Modal
        const modalEl = document.getElementById('eventModal');
        const modal = bootstrap.Modal.getInstance(modalEl);
        modal.hide();

        // Reset Form
        document.getElementById("evtTitle").value = "";
        document.getElementById("evtDesc").value = "";

        fetchEvents();

    } catch(e) {
        console.error(e);
        alert("Error creating event: " + e.message);
    }
};

// B. View Participants (Fetch List)
window.viewParticipants = async function(eventId) {
    const list = document.getElementById("participantsList");
    if(!list) return;

    list.innerHTML = "<li class='list-group-item text-center'>Loading...</li>";

    // Open Modal
    new bootstrap.Modal(document.getElementById('participantsModal')).show();

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

// C. Toggle RSVP
window.toggleRSVP = async function(id) {
    try {
        await send(`/events/${id}/rsvp`, 'POST');
        fetchEvents();
    } catch(e) { console.error(e); }
};

// D. Delete Event
window.deleteEvent = async function(id) {
    if(confirm("Are you sure you want to delete this event?")) {
        await send(`/events/${id}`, 'DELETE');
        fetchEvents();
    }
};

// E. Edit Event (Open Modal)
window.openEditEvent = function(encodedEvent) {
    const event = JSON.parse(decodeURIComponent(encodedEvent));

    document.getElementById("editEventId").value = event.id;
    document.getElementById("editEventTitle").value = event.title;
    document.getElementById("editEventDesc").value = event.description;
    document.getElementById("editEventLoc").value = event.location;

    if(event.dateTime) {
        const dt = new Date(event.dateTime);
        const iso = dt.toISOString().slice(0, 16);
        document.getElementById("editEventDate").value = iso;
    }

    new bootstrap.Modal(document.getElementById('editEventModal')).show();
};

// F. Submit Edit (UPDATED: Clean Date Logic)
window.submitEditEvent = async function() {
    const id = document.getElementById("editEventId").value;
    const rawDate = document.getElementById("editEventDate").value;

    // Safety: Remove seconds if they exist
    const cleanDate = rawDate ? rawDate.slice(0, 16) : null;

    // Validation
    const title = document.getElementById("editEventTitle").value;
    if (!title || !cleanDate) {
        alert("Please provide at least a Title and a Date.");
        return;
    }

    const data = {
        title: title,
        description: document.getElementById("editEventDesc").value,
        location: document.getElementById("editEventLoc").value,
        dateTime: cleanDate
    };

    try {
        await send(`/events/${id}`, 'PUT', data);

        alert("✅ Event Updated!");
        bootstrap.Modal.getInstance(document.getElementById('editEventModal')).hide();
        fetchEvents();

    } catch (e) {
        console.error(e);
        alert("Failed to update event: " + e.message);
    }
};