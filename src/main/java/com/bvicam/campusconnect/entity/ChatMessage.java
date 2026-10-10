package com.bvicam.campusconnect.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * JPA Entity representing a relational chat message linked directly to {@link User} entities.
 *
 * <p><b>Database Mapping &amp; Relationships:</b></p>
 * <ul>
 *   <li>Maps to the {@code chat_message} table in MySQL.</li>
 *   <li>Maintains {@link ManyToOne} foreign-key associations to {@link User} via {@code sender_id}
 *       and {@code recipient_id} (inverse of {@code User.sentMessages} and {@code User.receivedMessages}).</li>
 *   <li><i>Note:</i> See also {@link PrivateMessage} (which stores email-based direct message history)
 *       and {@link com.bvicam.campusconnect.dto.ChatMessage} (the lightweight WebSocket DTO).</li>
 * </ul>
 */
@Entity
@Data
public class ChatMessage {

    /** Auto-incremented primary key uniquely identifying this chat message record. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The actual text message
    private String content;

    // When it was sent
    private LocalDateTime timestamp;

    // Who sent it? (Links to User table via sender_id foreign key)
    @ManyToOne
    @JoinColumn(name = "sender_id")
    private User sender;

    // Who received it? (Links to User table via recipient_id foreign key)
    @ManyToOne
    @JoinColumn(name = "recipient_id")
    private User recipient;
}