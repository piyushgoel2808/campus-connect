package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.ChatMessage;
import com.bvicam.campusconnect.dto.TypingMessage;
import com.bvicam.campusconnect.entity.PrivateMessage;
import com.bvicam.campusconnect.entity.NotificationType;
import com.bvicam.campusconnect.entity.User; // Added Import
import com.bvicam.campusconnect.repository.PrivateMessageRepository;
import com.bvicam.campusconnect.repository.UserRepository; // Added Import
import com.bvicam.campusconnect.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

@Controller
public class ChatController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private PrivateMessageRepository messageRepo;

    @Autowired
    private UserRepository userRepository; // Added missing repository

    @Autowired
    private NotificationService notificationService;

    // --- PUBLIC CHAT ---
    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/public")
    public ChatMessage sendMessage(@Payload ChatMessage chatMessage, Principal principal) {
        if (principal != null) {
            chatMessage.setSenderName(principal.getName());
        }
        return chatMessage;
    }

    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public")
    public ChatMessage addUser(@Payload ChatMessage chatMessage, Principal principal) {
        if (principal != null) {
            chatMessage.setSenderName(principal.getName());
        }
        return chatMessage;
    }

    // --- PRIVATE CHAT (Real-Time) ---
    @MessageMapping("/chat.private")
    public void sendPrivateMessage(@Payload ChatMessage message, Principal principal) {
        String senderEmail = principal != null ? principal.getName() : message.getSenderName();
        String receiverEmail = message.getReceiverName();

        if (senderEmail == null || receiverEmail == null) {
            return;
        }

        message.setSenderName(senderEmail);
        message.setReceiverName(receiverEmail);

        // 1. Save to Database
        PrivateMessage pm = new PrivateMessage();
        pm.setSenderEmail(senderEmail);
        pm.setReceiverEmail(receiverEmail);
        pm.setContent(message.getContent());
        messageRepo.save(pm);

        // 2. Send to Receiver (Specific User Queue)
        // Format: /user/{email}/queue/messages
        messagingTemplate.convertAndSendToUser(
                receiverEmail,
                "/queue/messages",
                message
        );

        // 3. Send back to Sender (so they see their own message instantly)
        messagingTemplate.convertAndSendToUser(
                senderEmail,
                "/queue/messages",
                message
        );

        // 4. Create in-app notification for receiver
        if (notificationService != null) {
            notificationService.createAndDispatch(
                receiverEmail,
                NotificationType.MESSAGE,
                "New message",
                "You received a new message from " + senderEmail,
                pm.getId()
            );
        }
    }

    // --- TYPING INDICATOR ---
    @MessageMapping("/chat.typing")
    public void typingIndicator(@Payload TypingMessage message, Principal principal) {
        if (principal != null) {
            message.setSenderEmail(principal.getName());
        }

        messagingTemplate.convertAndSendToUser(
                message.getReceiverEmail(),
                "/queue/typing",
                message
        );
    }

    // --- FETCH HISTORY (API) ---
    @GetMapping("/api/messages/history")
    @ResponseBody
    public ResponseEntity<List<PrivateMessage>> getHistory(@RequestParam String partnerEmail) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).build();
        }

        String myEmail = authentication.getName();
        return ResponseEntity.ok(messageRepo.findConversation(myEmail, partnerEmail));
    }

    // --- GET RECENT CONVERSATIONS (Partners) ---
    @GetMapping("/api/messages/partners")
    @ResponseBody
    public ResponseEntity<?> getChatPartners() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).build();
        }

        String myEmail = authentication.getName();

        // 1. Fetch all messages involving ME
        List<PrivateMessage> allMyMessages = messageRepo.findAll().stream()
                .filter(m -> m.getSenderEmail().equals(myEmail) || m.getReceiverEmail().equals(myEmail))
                .sorted((m1, m2) -> m2.getTimestamp().compareTo(m1.getTimestamp())) // Newest first
                .collect(Collectors.toList());

        // 2. Extract unique partners from the messages
        Set<String> partnersEmails = new LinkedHashSet<>();
        for (PrivateMessage m : allMyMessages) {
            if (m.getSenderEmail().equals(myEmail)) {
                partnersEmails.add(m.getReceiverEmail());
            } else {
                partnersEmails.add(m.getSenderEmail());
            }
        }

        // 3. Fetch User Details for these emails
        List<User> partners = userRepository.findAll().stream()
                .filter(u -> partnersEmails.contains(u.getEmail()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(partners);
    }
}