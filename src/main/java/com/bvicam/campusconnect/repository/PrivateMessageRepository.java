package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.PrivateMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface PrivateMessageRepository extends JpaRepository<PrivateMessage, Long> {
    // Fetch conversation between two users
    @Query("SELECT m FROM PrivateMessage m WHERE (m.senderEmail = ?1 AND m.receiverEmail = ?2) OR (m.senderEmail = ?2 AND m.receiverEmail = ?1) ORDER BY m.timestamp ASC")
    List<PrivateMessage> findConversation(String user1, String user2);
}