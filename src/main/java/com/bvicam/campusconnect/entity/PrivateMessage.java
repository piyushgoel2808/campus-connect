package com.bvicam.campusconnect.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * JPA Entity representing a persisted 1-on-1 direct message between two users, indexed by their email addresses.
 *
 * <p><b>Database Table &amp; Architectural Role:</b></p>
 * <ul>
 *   <li>Maps to the {@code private_messages} table in MySQL.</li>
 *   <li>Used by {@code ChatController} as both the incoming STOMP WebSocket payload ({@code /app/chat}),
 *       the persisted database record via {@code PrivateMessageRepository}, and the outgoing broadcast
 *       payload delivered to {@code /topic/messages/{receiverEmail}} and {@code /topic/messages/{senderEmail}}.</li>
 *   <li>Queried via {@code GET /api/messages/history/{user1}/{user2}} and {@code GET /api/messages/partners/{email}}
 *       to populate conversation history and recent chat contacts in the UI.</li>
 * </ul>
 */
@Entity
@Data
@Table(name = "private_messages")
public class PrivateMessage {

    /** Auto-incremented primary key uniquely identifying this private message. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Email address of the user who sent the message. */
    private String senderEmail;

    /** Email address of the user receiving the message. */
    private String receiverEmail;

    /** Plaintext content of the chat message. */
    private String content;

    /** Timestamp when the message was created/sent (defaults to current server time). */
    private LocalDateTime timestamp = LocalDateTime.now();
}