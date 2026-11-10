package com.bvicam.campusconnect.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * Legacy unused chat model retained only for source compatibility.
 * Active 1-on-1 persistence uses {@link PrivateMessage} and WebSocket transport uses
 * {@link com.bvicam.campusconnect.dto.ChatMessage}.
 */
@Deprecated(forRemoval = true)
@Data
public class ChatMessage {
    private Long id;
    private String content;
    private LocalDateTime timestamp;
    private User sender;
    private User recipient;
}
