package com.bvicam.campusconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String senderName;
    private String receiverName; // Null if it's a public message
    private String content;
    private MessageType type;    // CHAT, JOIN, LEAVE

    public enum MessageType {
        CHAT,
        JOIN,
        LEAVE
    }
}