/**
 * User Feedback & Star Rating modal controller (`#feedbackModal` in `dashboard.html`).
 */

import { send, parseErrorMessage } from '../utils/api.js';

window.openFeedbackModal = function() {
    document.getElementById("feedbackRating").value = "0";
    document.getElementById("feedbackComment").value = "";
    resetStars(0);

    const modalEl = document.getElementById('feedbackModal');
    if (modalEl) {
        bootstrap.Modal.getOrCreateInstance(modalEl).show();
    }
};

window.setRating = function(n) {
    document.getElementById("feedbackRating").value = n;
    resetStars(n);
};

function resetStars(n) {
    for (let i = 1; i <= 5; i++) {
        const star = document.getElementById(`star${i}`);
        if (!star) continue;
        if (i <= n) {
            star.classList.remove("far");
            star.classList.add("fas");
        } else {
            star.classList.remove("fas");
            star.classList.add("far");
        }
    }
}

window.submitFeedback = async function() {
    const rating = document.getElementById("feedbackRating").value;
    const comments = document.getElementById("feedbackComment").value.trim();

    if (rating == "0") return alert("Please select a star rating!");

    const btn = document.querySelector("button[onclick='window.submitFeedback()']");
    if (btn) { btn.innerText = "Submitting..."; btn.disabled = true; }

    try {
        const res = await send('/feedback', 'POST', { rating: parseInt(rating, 10), comments });

        if (res.ok) {
            alert("🎉 Thank you for your feedback!");
            const modalEl = document.getElementById('feedbackModal');
            if (modalEl) bootstrap.Modal.getOrCreateInstance(modalEl).hide();
        } else {
            alert("❌ Failed to submit feedback: " + await parseErrorMessage(res));
        }
    } catch (e) {
        console.error(e);
        alert("Error: " + e.message);
    } finally {
        if (btn) { btn.innerText = "Submit Feedback"; btn.disabled = false; }
    }
};
