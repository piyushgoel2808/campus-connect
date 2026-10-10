package com.bvicam.campusconnect.service;

import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for tracking moderation violations and managing user flag states.
 * <p>
 * When a user attempts to post or comment with prohibited language, {@link #flagUser(String)}
 * marks their account as flagged and increments their violation counter in an independent
 * transaction ({@link Propagation#REQUIRES_NEW}) so the flag is committed even when the
 * offending post/comment transaction rolls back.
 * </p>
 */
@Service
public class UserModerationService {

    @Autowired
    private UserRepository userRepository;

    /**
     * Flags a user immediately in an independent database transaction ({@code REQUIRES_NEW}).
     * <p>
     * Guaranteed to persist to MySQL even if the calling method (e.g., {@code PostService.createPost})
     * subsequently throws an {@link IllegalArgumentException} and rolls back its own transaction.
     * </p>
     *
     * @param email the email address of the user who triggered the moderation rule
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void flagUser(String email) {
        if (email == null) return;
        userRepository.findByEmail(email).ifPresent(user -> {
            // Mark user as flagged and increment their cumulative violation counter
            user.setIsFlagged(true);
            int currentCount = user.getFlagCount() != null ? user.getFlagCount() : 0;
            user.setFlagCount(currentCount + 1);
            // Flush immediately within the new transaction
            userRepository.saveAndFlush(user);
        });
    }

    /**
     * Clears the moderation flag and resets the violation counter on a user account (Admin action).
     *
     * @param userId the ID of the user account to unflag
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
