import { get, send, getCurrentUser } from '../utils/api.js';

// Logic from your Section 8
export async function fetchJobs() {
    try {
        const jobs = await get('/jobs');
        renderJobs(jobs);
    } catch (e) { console.error(e); }
}

function renderJobs(jobs) {
    const list = document.getElementById("jobList");
    if (!list) return;
    list.innerHTML = "";

    const { role } = getCurrentUser();

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
        const deleteBtn = (role === "ADMIN")
            ? `<button class="btn btn-sm btn-outline-danger" onclick="window.deleteJob(${j.id})" aria-label="Delete job"><i class="fas fa-trash"></i></button>`
            : "";

        const postedBy = j.postedBy?.name || 'Campus Connect';
        const titleInitial = (j.company || 'J').charAt(0).toUpperCase();

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
                    <div class="d-flex gap-2">${deleteBtn}</div>
                </div>
                <div class="cc-feature-card-body">
                    <p class="cc-feature-meta mb-3">${j.description}</p>
                    <div class="d-flex justify-content-between align-items-center flex-wrap gap-2">
                        <a href="${j.applyLink}" target="_blank" rel="noopener noreferrer" class="btn btn-primary btn-sm">Apply Now</a>
                        <span class="text-muted small">Tap to open application link</span>
                    </div>
                </div>
            </article>`;
    });
}

export async function postJob() {
    const data = {
        title: document.getElementById("jobTitle").value,
        company: document.getElementById("jobCompany").value,
        location: document.getElementById("jobLocation").value,
        description: document.getElementById("jobDesc").value,
        applyLink: document.getElementById("jobLink").value
    };
    await send('/jobs', 'POST', data);

    // Close modal using Bootstrap API
    const modalEl = document.getElementById('postJobModal');
    const modal = bootstrap.Modal.getInstance(modalEl);
    modal.hide();

    fetchJobs();
}

export async function deleteJob(id) {
    if (confirm("Delete this job?")) {
        await send(`/jobs/${id}`, 'DELETE');
        fetchJobs();
    }
}

// *** CRITICAL STEP ***
// Expose these functions to the global window so HTML onclick="..." works
window.deleteJob = deleteJob;
window.postJob = postJob;