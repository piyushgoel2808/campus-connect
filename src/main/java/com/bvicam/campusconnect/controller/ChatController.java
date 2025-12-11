package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.ChatMessage;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {

    // 1. Community Chat
    // When a user sends to "/app/chat.sendMessage",
    // Server broadcasts it to everyone listening on "/topic/public"
    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/public")
    public ChatMessage sendMessage(@Payload ChatMessage chatMessage) {
        return chatMessage;
    }

    // 2. User Joins Chat
    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public")
    public ChatMessage addUser(@Payload ChatMessage chatMessage,
                               SimpMessageHeaderAccessor headerAccessor) {
        // Add username in web socket session for later use
        headerAccessor.getSessionAttributes().put("username", chatMessage.getSenderName());
        return chatMessage;
    }
}