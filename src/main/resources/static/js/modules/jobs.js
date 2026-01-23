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

    jobs.forEach(j => {
        // Re-using your logic
        let deleteBtn = (role === "ADMIN") ?
            `<button class="btn btn-sm btn-danger float-end ms-2" onclick="window.deleteJob(${j.id})"><i class="fas fa-trash"></i></button>` : "";

        list.innerHTML += `
            <div class="card p-4 mb-3 shadow-sm job-card">
                <div class="d-flex justify-content-between align-items-center">
                    <h5 class="fw-bold mb-0">${j.title}</h5>
                    <div>${deleteBtn}</div>
                </div>
                <h6 class="text-muted mt-1 mb-2">${j.company} &bull; ${j.location || 'Remote'}</h6>
                <p class="mb-3">${j.description}</p>
                <a href="${j.applyLink}" target="_blank" class="btn btn-sm btn-outline-primary">Apply Now</a>
            </div>`;
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