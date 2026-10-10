package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.PrivateMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

/**
 * Spring Data JPA repository for managing one-to-one {@link PrivateMessage} entities.
 * <p>
 * Persists real-time WebSocket chat messages and provides a custom JPQL query
 * to reconstruct the full message history between any two users.
 * </p>
 */
public interface PrivateMessageRepository extends JpaRepository<PrivateMessage, Long> {

    /**
     * Retrieves the complete two-way conversation history between {@code user1} and {@code user2},
     * matching messages sent in either direction and ordering them chronologically (oldest to newest).
     *
     * @param user1 the email address of the first participant
     * @param user2 the email address of the second participant
     * @return a chronological list of {@link PrivateMessage} records exchanged between the two users
     */
    // Fetch conversation between two users
    @Query("SELECT m FROM PrivateMessage m WHERE (m.senderEmail = ?1 AND m.receiverEmail = ?2) OR (m.senderEmail = ?2 AND m.receiverEmail = ?1) ORDER BY m.timestamp ASC")
    List<PrivateMessage> findConversation(String user1, String user2);
}