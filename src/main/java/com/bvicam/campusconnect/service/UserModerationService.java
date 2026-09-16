package com.bvicam.campusconnect.service;

import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserModerationService {

    @Autowired
    private UserRepository userRepository;

    /**
     * Flags a user immediately in an independent database transaction.
     * Guaranteed to persist even if the calling method throws or rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void flagUser(String email) {
        if (email == null) return;
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setIsFlagged(true);
            int currentCount = user.getFlagCount() != null ? user.getFlagCount() : 0;
            user.setFlagCount(currentCount + 1);
            userRepository.saveAndFlush(user);
        });
    }

    /**
     * Resets flags on a user account (Admin action).
     */
    @Transactional
    public void unflagUser(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setIsFlagged(false);
            user.setFlagCount(0);
            userRepository.saveAndFlush(user);
        });
    }
}
