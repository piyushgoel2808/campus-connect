package com.bvicam.campusconnect.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight WebSocket DTO used to broadcast real-time typing status between two chat participants.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TypingMessage {
    private String senderEmail;
    private String receiverEmail;

    @JsonProperty("isTyping")
    private boolean isTyping;
}
