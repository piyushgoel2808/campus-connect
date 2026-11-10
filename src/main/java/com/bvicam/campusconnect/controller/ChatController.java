package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.ChatMessage;
import com.bvicam.campusconnect.dto.TypingMessage;
import com.bvicam.campusconnect.dto.UserProfileDto;
import com.bvicam.campusconnect.entity.NotificationType;
import com.bvicam.campusconnect.entity.PrivateMessage;
import com.bvicam.campusconnect.entity.User;
import com.bvicam.campusconnect.repository.PrivateMessageRepository;
import com.bvicam.campusconnect.repository.UserRepository;
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
import java.util.*;

/**
 * Hybrid WebSocket (STOMP) and REST Controller for real-time messaging.
 */
@Controller
public class ChatController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private PrivateMessageRepository messageRepo;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/public")
    public ChatMessage sendMessage(@Payload ChatMessage chatMessage, Principal principal) {
        if (principal == null || principal.getName() == null || chatMessage == null) {
            return null;
        }
        String content = chatMessage.getContent() != null ? chatMessage.getContent().trim() : "";
        if (content.isEmpty()) {
            return null;
        }
        chatMessage.setSenderName(principal.getName());
        chatMessage.setContent(content);
        return chatMessage;
    }

    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public")
    public ChatMessage addUser(@Payload ChatMessage chatMessage, Principal principal) {
        if (principal == null || principal.getName() == null || chatMessage == null) {
            return null;
        }
        chatMessage.setSenderName(principal.getName());
        return chatMessage;
    }

    @MessageMapping("/chat.private")
    public void sendPrivateMessage(@Payload ChatMessage message, Principal principal) {
        if (principal == null || principal.getName() == null || message == null) {
            return;
        }

        String senderEmail = principal.getName();
        String receiverEmail = message.getReceiverName() != null ? message.getReceiverName().trim() : null;
        String content = message.getContent() != null ? message.getContent().trim() : "";

        if (receiverEmail == null || receiverEmail.isEmpty() || content.isEmpty()) {
            return;
        }

        message.setSenderName(senderEmail);
        message.setReceiverName(receiverEmail);
        message.setContent(content);

        PrivateMessage pm = new PrivateMessage();
        pm.setSenderEmail(senderEmail);
        pm.setReceiverEmail(receiverEmail);
        pm.setContent(content);
        messageRepo.save(pm);

        messagingTemplate.convertAndSendToUser(receiverEmail, "/queue/messages", message);
        if (!senderEmail.equalsIgnoreCase(receiverEmail)) {
            messagingTemplate.convertAndSendToUser(senderEmail, "/queue/messages", message);
        }

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

    @MessageMapping("/chat.typing")
    public void typingIndicator(@Payload TypingMessage message, Principal principal) {
        if (principal == null || principal.getName() == null || message == null) {
            return;
        }
        String receiverEmail = message.getReceiverEmail() != null ? message.getReceiverEmail().trim() : null;
        if (receiverEmail == null || receiverEmail.isEmpty()) {
            return;
        }
        message.setSenderEmail(principal.getName());
        messagingTemplate.convertAndSendToUser(receiverEmail, "/queue/typing", message);
    }

    @GetMapping("/api/messages/history")
    @ResponseBody
    public ResponseEntity<List<PrivateMessage>> getHistory(@RequestParam String partnerEmail) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).build();
        }
        if (partnerEmail == null || partnerEmail.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String myEmail = authentication.getName();
        return ResponseEntity.ok(messageRepo.findConversation(myEmail, partnerEmail.trim()));
    }

    @GetMapping("/api/messages/partners")
    @ResponseBody
    public ResponseEntity<?> getChatPartners() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(401).build();
        }

        String myEmail = authentication.getName();
        List<PrivateMessage> myMessages = messageRepo.findBySenderEmailOrReceiverEmailOrderByTimestampDesc(myEmail, myEmail);

        // Preserve order of most recent interaction
        Set<String> partnersEmails = new LinkedHashSet<>();
        for (PrivateMessage m : myMessages) {
            if (myEmail.equalsIgnoreCase(m.getSenderEmail())) {
                if (!myEmail.equalsIgnoreCase(m.getReceiverEmail())) {
                    partnersEmails.add(m.getReceiverEmail());
                }
            } else {
                partnersEmails.add(m.getSenderEmail());
            }
        }

        if (partnersEmails.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        Map<String, User> byEmail = new HashMap<>();
        for (User u : userRepository.findByEmailIn(partnersEmails)) {
            byEmail.put(u.getEmail().toLowerCase(Locale.ROOT), u);
        }

        List<UserProfileDto> orderedPartners = new ArrayList<>();
        for (String email : partnersEmails) {
            User partner = byEmail.get(email.toLowerCase(Locale.ROOT));
            if (partner != null) {
                orderedPartners.add(UserProfileDto.fromEntity(partner));
            }
        }

        return ResponseEntity.ok(orderedPartners);
    }
}
