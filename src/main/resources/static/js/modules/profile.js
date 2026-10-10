/**
 * ============================================================================
 * FILE: src/main/resources/static/js/modules/profile.js
 * PURPOSE: Current user's Profile & Notification Preferences editor module
 *          (`components/profile.html`).
 *
 * OVERVIEW:
 *   Loads the logged-in user's profile data from `GET /api/users/me` into the
 *   form fields in `components/profile.html` (`#pHeadline`, `#pCompany`,
 *   `#pDesignation`, `#pSkills`, `#pExperience`, `#pLinkedin`, `#pGithub`, and
 *   notification checkboxes `#pNotifyMessages`, `#pNotifyEvents`, `#pNotifyJobs`),
 *   and saves updates via `PUT /api/users/profile` (`window.saveProfile`).
 *
 * API ENDPOINTS USED:
 *   - GET `/api/users/me`      : Fetch current authenticated user's profile
 *   - PUT `/api/users/profile` : Save updated profile and notification settings
 * ============================================================================
 */

import { get, send } from '../utils/api.js';

// =========================================================
// 1. LOAD PROFILE
// =========================================================

/**
 * Fetches the authenticated user's profile from `GET /api/users/me` and
 * populates the input fields and notification toggle switches in `components/profile.html`.
 */
export async function loadProfile() {
    // Only try to load if we are actually on the profile tab
    // (though usually this function is called specifically when that tab loads)
    try {
        const user = await get('/users/me');

        // Helper to safely set values only if the input exists
        const setVal = (id, val) => {
            const el = document.getElementById(id);
            if(el) el.value = val || "";
        };

        setVal("pHeadline", user.headline);
        setVal("pCompany", user.currentCompany);
        setVal("pDesignation", user.designation);
        setVal("pSkills", user.skills);
        setVal("pExperience", user.pastExperience);
        setVal("pLinkedin", user.linkedinUrl);
        setVal("pGithub", user.githubUrl);

        // Helper to set checkbox state (defaults to true unless explicitly false)
        const setChecked = (id, val) => {
            const el = document.getElementById(id);
            if (el) el.checked = val !== false;
        };

        setChecked("pNotifyMessages", user.notifyMessages);
        setChecked("pNotifyEvents", user.notifyEvents);
        setChecked("pNotifyJobs", user.notifyJobs);

    } catch (e) {
        console.error("Failed to load profile data", e);
    }
}

// =========================================================
// 2. SAVE PROFILE (Exposed to Window)
// =========================================================

/**
 * Reads all profile inputs and notification checkboxes from `components/profile.html`
 * and sends `PUT /api/users/profile`. Reloads the profile form on success.
 */
window.saveProfile = async function() {
    // Helper to get values safely
    const getVal = (id) => {
        const el = document.getElementById(id);
        return el ? el.value : "";
    };

    const data = {
        headline: getVal("pHeadline"),
        currentCompany: getVal("pCompany"),
        designation: getVal("pDesignation"),
        skills: getVal("pSkills"),
        pastExperience: getVal("pExperience"),
        linkedinUrl: getVal("pLinkedin"),
        githubUrl: getVal("pGithub"),
        notifyMessages: !!document.getElementById("pNotifyMessages")?.checked,
        notifyEvents: !!document.getElementById("pNotifyEvents")?.checked,
        notifyJobs: !!document.getElementById("pNotifyJobs")?.checked
    };

    const btn = document.querySelector("button[onclick='saveProfile()']");
    if(btn) {
        btn.innerHTML = "Saving...";
        btn.disabled = true;
    }

    try {
        const res = await send('/users/profile', 'PUT', data);

        if (res.ok) {
            alert("✅ Profile Updated Successfully!");
            // Reload to ensure data persistence is visible
            loadProfile();
        } else {
            alert("❌ Failed to update profile.");
        }
    } catch (e) {
        console.error(e);
        alert("❌ Error: " + e.message);
    } finally {
        if(btn) {
            btn.innerHTML = "Save Changes";
            btn.disabled = false;
        }
    }
};