package com.bvicam.campusconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TypingMessage {
    private String senderEmail;
    private String receiverEmail;
    private boolean isTyping;
}
