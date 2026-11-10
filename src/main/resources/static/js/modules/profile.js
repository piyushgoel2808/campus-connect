/**
 * Current user's Profile, Notification Preferences, and Password Update controller (`components/profile.html`).
 */

import { get, send, parseErrorMessage } from '../utils/api.js';

const SAVE_PROFILE_BTN_HTML = '<i class="fas fa-save me-1"></i> Save Profile';
const CHANGE_PWD_BTN_HTML = '<i class="fas fa-key me-1"></i> Update Password';

export async function loadProfile() {
    try {
        const user = await get('/users/me');
        if (!user) return;

        const setVal = (id, val) => {
            const el = document.getElementById(id);
            if (el) el.value = val || "";
        };

        setVal("pHeadline", user.headline);
        setVal("pCompany", user.currentCompany);
        setVal("pDesignation", user.designation);
        setVal("pSkills", user.skills);
        setVal("pExperience", user.pastExperience);
        setVal("pLinkedin", user.linkedinUrl);
        setVal("pGithub", user.githubUrl);

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

window.saveProfile = async function() {
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
        linkedinUrl: getVal("pLinkedin").trim(),
        githubUrl: getVal("pGithub").trim(),
        notifyMessages: !!document.getElementById("pNotifyMessages")?.checked,
        notifyEvents: !!document.getElementById("pNotifyEvents")?.checked,
        notifyJobs: !!document.getElementById("pNotifyJobs")?.checked
    };

    const btn = document.getElementById("btnSaveProfile") || document.querySelector("button[onclick='saveProfile()']");
    if (btn) {
        btn.innerText = "Saving...";
        btn.disabled = true;
    }

    try {
        const res = await send('/users/profile', 'PUT', data);

        if (res.ok) {
            alert("✅ Profile Updated Successfully!");
            await loadProfile();
        } else {
            const errMsg = await parseErrorMessage(res, "Failed to update profile.");
            alert(`❌ ${errMsg}`);
        }
    } catch (e) {
        console.error(e);
        alert("❌ Error: " + e.message);
    } finally {
        if (btn) {
            btn.innerHTML = SAVE_PROFILE_BTN_HTML;
            btn.disabled = false;
        }
    }
};

window.changeMyPassword = async function() {
    const currentPassword = document.getElementById("pCurrentPassword")?.value || "";
    const newPassword = document.getElementById("pNewPassword")?.value || "";
    const confirmPassword = document.getElementById("pConfirmPassword")?.value || "";
    const alertEl = document.getElementById("pwdAlert");
    const btn = document.getElementById("btnChangePassword");

    const showPwdAlert = (msg, ok = false) => {
        if (!alertEl) return alert(msg);
        alertEl.className = ok ? "alert alert-success mb-3" : "alert alert-danger mb-3";
        alertEl.innerText = msg;
        alertEl.classList.remove("d-none");
    };

    if (!currentPassword) {
        showPwdAlert("Please enter your current password.");
        return;
    }
    if (newPassword.length < 6) {
        showPwdAlert("New password must be at least 6 characters long.");
        return;
    }
    if (newPassword !== confirmPassword) {
        showPwdAlert("New password and confirmation do not match.");
        return;
    }

    if (btn) {
        btn.innerText = "Updating...";
        btn.disabled = true;
    }

    try {
        const res = await send('/auth/change-password', 'POST', { currentPassword, newPassword });
        if (res.ok) {
            localStorage.setItem("password_changed", "true");
            const banner = document.getElementById("defaultPasswordBanner");
            if (banner) banner.classList.add("d-none");
            document.getElementById("pCurrentPassword").value = "";
            document.getElementById("pNewPassword").value = "";
            document.getElementById("pConfirmPassword").value = "";
            showPwdAlert("✅ Password updated successfully!", true);
        } else {
            const errMsg = await parseErrorMessage(res, "Failed to update password.");
            showPwdAlert(`❌ ${errMsg}`);
        }
    } catch (e) {
        console.error(e);
        showPwdAlert("❌ Error: " + e.message);
    } finally {
        if (btn) {
            btn.innerHTML = CHANGE_PWD_BTN_HTML;
            btn.disabled = false;
        }
    }
};
