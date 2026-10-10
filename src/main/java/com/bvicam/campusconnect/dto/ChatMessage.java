package com.bvicam.campusconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) representing a real-time STOMP WebSocket chat event payload.
 *
 * <p><b>Architectural Note:</b></p>
 * <ul>
 *   <li>Models transient WebSocket chat payloads (including presence events like {@link MessageType#JOIN}
 *       and {@link MessageType#LEAVE} as well as {@link MessageType#CHAT} messages).</li>
 *   <li>Distinct from {@link com.bvicam.campusconnect.entity.ChatMessage} and
 *       {@link com.bvicam.campusconnect.entity.PrivateMessage}, which represent persisted database records.</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    /** Identifier or display name of the user sending the message. */
    private String senderName;

    /** Identifier of the recipient user; {@code null} if broadcasting to a public room/topic. */
    private String receiverName; // Null if it's a public message

    /** Text body of the chat message. */
    private String content;

    /** Type of real-time chat event ({@code CHAT}, {@code JOIN}, or {@code LEAVE}). */
    private MessageType type;    // CHAT, JOIN, LEAVE

    /**
     * Discriminator enum indicating the lifecycle type of a WebSocket chat frame.
     */
    public enum MessageType {
        /** Standard text message sent during a conversation. */
        CHAT,
        /** Presence notification indicating a user joined the chat session. */
        JOIN,
        /** Presence notification indicating a user disconnected or left the chat session. */
        LEAVE
    }
}