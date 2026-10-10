package com.bvicam.campusconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight WebSocket DTO used to broadcast real-time typing status between two chat participants.
 *
 * <p><b>STOMP WebSocket Flow:</b></p>
 * <ol>
 *   <li>When a user types or pauses in the chat input box, the browser sends this payload to
 *       {@code /app/typing}.</li>
 *   <li>{@code ChatController.handleTyping} forwards the payload directly to
 *       {@code /topic/typing/{receiverEmail}} without persisting anything to MySQL.</li>
 * </ol>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TypingMessage {
    /** Email address of the user who is currently typing (or stopped typing). */
    private String senderEmail;

    /** Email address of the chat partner who should see the "is typing..." indicator. */
    private String receiverEmail;

    /** {@code true} if the sender is actively typing; {@code false} when they pause or send the message. */
    private boolean isTyping;
}

