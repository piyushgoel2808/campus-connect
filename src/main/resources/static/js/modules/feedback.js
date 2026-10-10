/**
 * ============================================================================
 * FILE: src/main/resources/static/js/modules/feedback.js
 * PURPOSE: User Feedback & Star Rating modal controller (`#feedbackModal` in `dashboard.html`).
 *
 * OVERVIEW:
 *   Allows any logged-in user to open the Feedback modal from the top navbar
 *   (`window.openFeedbackModal`), select a 1–5 star rating (`window.setRating`),
 *   write optional comments, and submit the rating to `POST /api/feedback`
 *   (`window.submitFeedback`). Submitted feedback can be reviewed by admins
 *   in the Admin Panel (`admin.js` -> `loadAdminFeedback()`).
 *
 * API ENDPOINTS USED:
 *   - POST `/api/feedback` : Submits `{ rating: number, comments: string }`.
 * ============================================================================
 */

import { send } from '../utils/api.js';

/**
 * 1. OPEN MODAL
 * Resets the hidden rating input (`#feedbackRating`), clears `#feedbackComment`,
 * resets all 5 star icons to unselected outline state, and shows `#feedbackModal`.
 */
window.openFeedbackModal = function() {
    // Reset form
    document.getElementById("feedbackRating").value = "0";
    document.getElementById("feedbackComment").value = "";
    resetStars(0);

    new bootstrap.Modal(document.getElementById('feedbackModal')).show();
};

/**
 * 2. HANDLE STAR CLICKS (Visuals)
 * Updates `#feedbackRating` when the user clicks star `n` (1..5) and highlights
 * stars `1..n`.
 *
 * @param {number} n - Selected star rating between 1 and 5.
 */
window.setRating = function(n) {
    document.getElementById("feedbackRating").value = n;
    resetStars(n);
};

/**
 * Toggles FontAwesome solid (`fas`) vs outline (`far`) classes on `#star1`..`#star5`.
 *
 * @param {number} n - Number of stars to fill (0 clears all stars).
 */
function resetStars(n) {
    for (let i = 1; i <= 5; i++) {
        const star = document.getElementById(`star${i}`);
        if (i <= n) {
            star.classList.remove("far"); // Empty star
            star.classList.add("fas");    // Solid star
        } else {
            star.classList.remove("fas");
            star.classList.add("far");
        }
    }
}

/**
 * 3. SUBMIT TO BACKEND
 * Validates that at least 1 star was chosen and sends `POST /api/feedback`.
 */
window.submitFeedback = async function() {
    const rating = document.getElementById("feedbackRating").value;
    const comments = document.getElementById("feedbackComment").value;

    if (rating == "0") return alert("Please select a star rating!");

    const btn = document.querySelector("button[onclick='window.submitFeedback()']");
    if(btn) { btn.innerText = "Submitting..."; btn.disabled = true; }

    try {
        const res = await send('/feedback', 'POST', { rating: parseInt(rating), comments });

        if (res.ok) {
            alert("🎉 Thank you for your feedback!");
            bootstrap.Modal.getInstance(document.getElementById('feedbackModal')).hide();
        } else {
            alert("Failed to submit feedback.");
        }
    } catch (e) {
        console.error(e);
        alert("Error: " + e.message);
    } finally {
        if(btn) { btn.innerText = "Submit Feedback"; btn.disabled = false; }
    }
};