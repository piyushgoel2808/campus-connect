import { send } from '../utils/api.js';

// 1. OPEN MODAL
window.openFeedbackModal = function() {
    // Reset form
    document.getElementById("feedbackRating").value = "0";
    document.getElementById("feedbackComment").value = "";
    resetStars(0);

    new bootstrap.Modal(document.getElementById('feedbackModal')).show();
};

// 2. HANDLE STAR CLICKS (Visuals)
window.setRating = function(n) {
    document.getElementById("feedbackRating").value = n;
    resetStars(n);
};

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

// 3. SUBMIT TO BACKEND
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