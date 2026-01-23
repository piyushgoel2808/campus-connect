import { get, send } from '../utils/api.js';

// =========================================================
// 1. LOAD PROFILE
// =========================================================

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

    } catch (e) {
        console.error("Failed to load profile data", e);
    }
}

// =========================================================
// 2. SAVE PROFILE (Exposed to Window)
// =========================================================

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
        githubUrl: getVal("pGithub")
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