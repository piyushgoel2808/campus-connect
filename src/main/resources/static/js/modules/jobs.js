/**
 * ============================================================================
 * FILE: src/main/resources/static/js/modules/jobs.js
 * PURPOSE: Jobs & Referrals board controller (`components/jobs.html` and
 *          shared modals in `dashboard.html`).
 *
 * OVERVIEW:
 *   Fetches job and internship referral postings from `GET /api/jobs` and
 *   renders cards into `#jobList`.
 *   Role-based permissions:
 *     - `ALUMNI` and `ADMIN` users can post new jobs (`#btnPostJob` -> `#postJobModal`).
 *     - A job's original author (`isOwner`) or any `ADMIN` can edit (`#editJobModal`)
 *       or delete (`window.deleteJob`) the posting.
 *     - Also syncs with the Admin Panel's Jobs table (`window.loadAdminJobs`)
 *       whenever a job is created, edited, or deleted.
 *
 * API ENDPOINTS USED:
 *   - GET    `/api/jobs`      : Fetch all job/referral listings
 *   - POST   `/api/jobs`      : Create a new job/referral (Alumni / Admin)
 *   - PUT    `/api/jobs/{id}` : Update an existing job/referral (Owner / Admin)
 *   - DELETE `/api/jobs/{id}` : Delete a job/referral (Owner / Admin)
 * ============================================================================
 */

import { get, send, getCurrentUser } from '../utils/api.js';

// In-memory cache mapping jobId -> job object for modal editing
const jobsMap = new Map();

/**
 * Resolves the current user's normalized uppercase role and email from
 * `getCurrentUser()`, falling back to `campus_user` in `localStorage` if needed.
 *
 * @returns {{ role: string, email: string }}
 */
function resolveCurrentRoleAndEmail() {
    let { role, email } = getCurrentUser();
    if (!role || !email) {
        try {
            const stored = JSON.parse(localStorage.getItem("campus_user") || "{}");
            if (!role && stored.role) role = stored.role;
            if (!email && stored.email) email = stored.email;
        } catch (_) {}
    }
    return {
        role: role ? role.toUpperCase() : "",
        email: email || ""
    };
}

/**
 * Fetches all job postings from `GET /api/jobs`, reveals `#btnPostJob` if the
 * user is `ADMIN` or `ALUMNI`, and renders the job cards into `#jobList`.
 */
export async function fetchJobs() {
    try {
        const { role } = resolveCurrentRoleAndEmail();
        const postJobBtn = document.getElementById("btnPostJob");
        if (postJobBtn && (role === "ADMIN" || role === "ALUMNI")) {
            postJobBtn.classList.remove("d-none");
        }

        const jobs = await get('/jobs');
        renderJobs(jobs);
    } catch (e) {
        console.error("Failed to fetch jobs:", e);
    }
}

/**
 * Normalizes an application link or email into a valid `href` attribute:
 *   - Preserves `http://`, `https://`, and `mailto:` links as-is.
 *   - Prefixes bare email addresses with `mailto:`.
 *   - Prefixes bare domain URLs with `https://`.
 *
 * @param {string} link - Raw apply link or email entered by the poster.
 * @returns {string} Safe href string for the "Apply Now" anchor tag.
 */
function formatApplyHref(link) {
    if (!link) return "#";
    const trimmed = link.trim();
    if (/^(https?:\/\/|mailto:)/i.test(trimmed)) return trimmed;
    if (trimmed.includes("@") && !trimmed.includes("/")) return `mailto:${trimmed}`;
    return `https://${trimmed}`;
}

/**
 * Renders job/referral cards inside `#jobList` and caches each job in `jobsMap`.
 * Displays Edit and Delete buttons if the logged-in user is an `ADMIN` or the job's author.
 *
 * @param {Array<Object>} jobs - Array of job objects returned from `/api/jobs`.
 */
function renderJobs(jobs) {
    const list = document.getElementById("jobList");
    jobsMap.clear();
    (jobs || []).forEach(j => jobsMap.set(j.id, j));

    if (!list) return;
    list.innerHTML = "";

    const { role, email } = resolveCurrentRoleAndEmail();
    const isAdmin = role === "ADMIN";

    if (!jobs || jobs.length === 0) {
        list.innerHTML = `
            <div class="cc-empty-state cc-panel">
                <i class="fas fa-briefcase fa-lg"></i>
                <h5 class="mb-2">No jobs posted yet</h5>
                <p class="mb-0">Check back later for new opportunities.</p>
            </div>`;
        return;
    }

    jobs.forEach(j => {
        const isOwner = email && j.postedBy?.email && j.postedBy.email.toLowerCase() === email.toLowerCase();
        const canManage = isAdmin || isOwner;

        const actionBtns = canManage
            ? `
                <button class="btn btn-sm btn-outline-primary" onclick="window.openEditJobModal(${j.id})" title="Edit Job / Referral" aria-label="Edit job">
                    <i class="fas fa-edit me-1"></i> Edit
                </button>
                <button class="btn btn-sm btn-outline-danger" onclick="window.deleteJob(${j.id})" title="Delete Job / Referral" aria-label="Delete job">
                    <i class="fas fa-trash"></i>
                </button>`
            : "";

        const postedBy = j.postedBy?.name || 'GradLink';
        const titleInitial = (j.company || 'J').charAt(0).toUpperCase();
        const applyHref = formatApplyHref(j.applyLink);

        list.innerHTML += `
            <article class="cc-feature-card cc-hover-lift">
                <div class="cc-feature-card-header">
                    <div class="d-flex align-items-start gap-3 flex-grow-1">
                        <div class="avatar-circle">${titleInitial}</div>
                        <div>
                            <h5 class="cc-feature-title mb-1">${j.title}</h5>
                            <div class="cc-feature-meta">${j.company} • ${j.location || 'Remote'}</div>
                            <div class="mt-2 d-flex flex-wrap gap-2">
                                <span class="badge rounded-pill text-bg-light border">Posted by ${postedBy}</span>
                                <span class="badge rounded-pill text-bg-light border">Opportunity</span>
                            </div>
                        </div>
                    </div>
                    <div class="d-flex gap-2 align-items-start">${actionBtns}</div>
                </div>
                <div class="cc-feature-card-body">
                    <p class="cc-feature-meta mb-3">${j.description || ''}</p>
                    <div class="d-flex justify-content-between align-items-center flex-wrap gap-2">
                        <a href="${applyHref}" target="_blank" rel="noopener noreferrer" class="btn btn-primary btn-sm">Apply Now</a>
                        <span class="text-muted small">${j.applyLink || 'Tap to open application link'}</span>
                    </div>
                </div>
            </article>`;
    });
}

/**
 * Opens the shared `#postJobModal` dialog (defined in `dashboard.html`).
 */
window.openPostJobModal = function() {
    const modalEl = document.getElementById('postJobModal');
    if (modalEl) {
        bootstrap.Modal.getOrCreateInstance(modalEl).show();
    }
};

/**
 * Reads form fields from `#postJobModal`, validates required fields, and submits
 * `POST /api/jobs`. Refreshes both `#jobList` and `#adminJobsTable` on success.
 */
export async function postJob() {
    const titleEl = document.getElementById("jobTitle");
    const companyEl = document.getElementById("jobCompany");
    const locationEl = document.getElementById("jobLocation");
    const descEl = document.getElementById("jobDesc");
    const linkEl = document.getElementById("jobLink");

    const data = {
        title: titleEl ? titleEl.value.trim() : "",
        company: companyEl ? companyEl.value.trim() : "",
        location: locationEl ? locationEl.value.trim() : "",
        description: descEl ? descEl.value.trim() : "",
        applyLink: linkEl ? linkEl.value.trim() : ""
    };

    if (!data.title || !data.company) {
        alert("Please enter both a Job Title and Company Name.");
        return;
    }

    try {
        const res = await send('/jobs', 'POST', data);
        if (!res.ok) {
            const errText = await res.text();
            alert("Failed to post job: " + errText);
            return;
        }

        alert("✅ Job / Referral posted successfully!");

        // Reset fields
        if (titleEl) titleEl.value = "";
        if (companyEl) companyEl.value = "";
        if (locationEl) locationEl.value = "";
        if (descEl) descEl.value = "";
        if (linkEl) linkEl.value = "";

        // Close modal using Bootstrap API
        const modalEl = document.getElementById('postJobModal');
        if (modalEl) {
            bootstrap.Modal.getOrCreateInstance(modalEl).hide();
        }

        await fetchJobs();
        if (window.loadAdminJobs) window.loadAdminJobs();
    } catch (e) {
        console.error("Error posting job:", e);
        alert("Error posting job: " + e.message);
    }
}

/**
 * Populates `#editJobModal` with the selected job's details (fetching `/api/jobs`
 * if `jobsMap` is not yet populated, e.g., when invoked directly from Admin Panel)
 * and opens the modal.
 *
 * @param {number|Object} jobOrId - Job ID or job object to edit.
 */
window.openEditJobModal = async function(jobOrId) {
    let job = (typeof jobOrId === 'object' && jobOrId !== null)
        ? jobOrId
        : jobsMap.get(Number(jobOrId));

    if (!job) {
        try {
            const allJobs = await get('/jobs');
            (allJobs || []).forEach(j => jobsMap.set(j.id, j));
            job = jobsMap.get(Number(jobOrId));
        } catch (e) {
            console.error(e);
        }
    }

    if (!job) {
        alert("Could not load job details.");
        return;
    }

    const setField = (id, val) => {
        const el = document.getElementById(id);
        if (el) el.value = val ?? "";
    };

    setField("editJobId", job.id);
    setField("editJobTitle", job.title || "");
    setField("editJobCompany", job.company || "");
    setField("editJobLocation", job.location || "");
    setField("editJobDesc", job.description || "");
    setField("editJobLink", job.applyLink || "");

    const modalEl = document.getElementById('editJobModal');
    if (modalEl) {
        bootstrap.Modal.getOrCreateInstance(modalEl).show();
    }
};

/**
 * Submits edited job fields from `#editJobModal` to `PUT /api/jobs/{id}`
 * and refreshes both the Jobs board and Admin Jobs table.
 */
window.submitEditJob = async function() {
    const id = document.getElementById("editJobId")?.value;
    const title = document.getElementById("editJobTitle")?.value.trim() || "";
    const company = document.getElementById("editJobCompany")?.value.trim() || "";
    const location = document.getElementById("editJobLocation")?.value.trim() || "";
    const description = document.getElementById("editJobDesc")?.value.trim() || "";
    const applyLink = document.getElementById("editJobLink")?.value.trim() || "";

    if (!id || !title || !company) {
        alert("Job Title and Company Name are required!");
        return;
    }

    try {
        const res = await send(`/jobs/${id}`, 'PUT', {
            title,
            company,
            location,
            description,
            applyLink
        });

        if (!res.ok) {
            const errText = await res.text();
            alert("Failed to update job: " + errText);
            return;
        }

        alert("✅ Job / Referral updated successfully!");

        const modalEl = document.getElementById('editJobModal');
        if (modalEl) {
            bootstrap.Modal.getOrCreateInstance(modalEl).hide();
        }

        await fetchJobs();
        if (window.loadAdminJobs) window.loadAdminJobs();
    } catch (e) {
        console.error("Error updating job:", e);
        alert("Error updating job: " + e.message);
    }
};

/**
 * Deletes a job posting via `DELETE /api/jobs/{id}` after user confirmation
 * and refreshes both the Jobs board and Admin Jobs table.
 *
 * @param {number} id - Target job ID to delete.
 */
export async function deleteJob(id) {
    if (confirm("Are you sure you want to delete this job / referral?")) {
        const res = await send(`/jobs/${id}`, 'DELETE');
        if (!res.ok) {
            const err = await res.text();
            alert("Failed to delete job: " + err);
            return;
        }
        await fetchJobs();
        if (window.loadAdminJobs) window.loadAdminJobs();
    }
}

// Expose these functions to the global window so HTML onclick="..." works
window.deleteJob = deleteJob;
window.postJob = postJob;
window.fetchJobs = fetchJobs;