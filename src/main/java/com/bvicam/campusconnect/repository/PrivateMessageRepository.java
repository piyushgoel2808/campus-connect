package com.bvicam.campusconnect.repository;

import com.bvicam.campusconnect.entity.PrivateMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA repository for managing one-to-one {@link PrivateMessage} entities.
 */
public interface PrivateMessageRepository extends JpaRepository<PrivateMessage, Long> {

    @Query("SELECT m FROM PrivateMessage m WHERE (m.senderEmail = ?1 AND m.receiverEmail = ?2) OR (m.senderEmail = ?2 AND m.receiverEmail = ?1) ORDER BY m.timestamp ASC")
    List<PrivateMessage> findConversation(String user1, String user2);

    List<PrivateMessage> findBySenderEmailOrReceiverEmailOrderByTimestampDesc(String senderEmail, String receiverEmail);

    @Modifying
    @Query("DELETE FROM PrivateMessage m WHERE m.senderEmail = :email OR m.receiverEmail = :email")
    void deleteAllByParticipantEmail(@Param("email") String email);
}
