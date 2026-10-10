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

/**
 * Hybrid WebSocket (STOMP) and REST Controller for real-time messaging.
 * <p>
 * Handles:
 * </p>
 * <ul>
 *   <li>Public broadcast chat messages ({@code /app/chat.sendMessage} -> {@code /topic/public})</li>
 *   <li>Private 1-on-1 messages ({@code /app/chat.private} -> {@code /user/{email}/queue/messages}) with database persistence and real-time notifications</li>
 *   <li>Real-time typing indicators ({@code /app/chat.typing} -> {@code /user/{email}/queue/typing})</li>
 *   <li>REST endpoints for loading conversation history ({@code GET /api/messages/history}) and recent chat partners ({@code GET /api/messages/partners})</li>
 * </ul>
 */
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

    // =========================================================
    // 1. PUBLIC CHAT (STOMP WEBSOCKET)
    // =========================================================

    /**
     * Broadcasts a message to the public campus chat room.
     * <ul>
     *   <li><b>WebSocket Destination:</b> {@code /app/chat.sendMessage}</li>
     *   <li><b>Broadcast Topic:</b> {@code /topic/public}</li>
     *   <li><b>Access Role:</b> Any Authenticated User</li>
     * </ul>
     *
     * @param chatMessage the incoming chat payload
     * @param principal   the authenticated WebSocket user principal
     * @return the {@link ChatMessage} broadcast to all subscribers of {@code /topic/public}
     */
    // --- PUBLIC CHAT ---
    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/public")
    public ChatMessage sendMessage(@Payload ChatMessage chatMessage, Principal principal) {
        if (principal != null) {
            chatMessage.setSenderName(principal.getName());
        }
        return chatMessage;
    }

    /**
     * Broadcasts a user join event to the public campus chat room.
     * <ul>
     *   <li><b>WebSocket Destination:</b> {@code /app/chat.addUser}</li>
     *   <li><b>Broadcast Topic:</b> {@code /topic/public}</li>
     *   <li><b>Access Role:</b> Any Authenticated User</li>
     * </ul>
     *
     * @param chatMessage the join event message payload
     * @param principal   the authenticated WebSocket user principal
     * @return the {@link ChatMessage} broadcast to {@code /topic/public}
     */
    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public")
    public ChatMessage addUser(@Payload ChatMessage chatMessage, Principal principal) {
        if (principal != null) {
            chatMessage.setSenderName(principal.getName());
        }
        return chatMessage;
    }

    // =========================================================
    // 2. PRIVATE 1-ON-1 CHAT & TYPING INDICATOR (STOMP WEBSOCKET)
    // =========================================================

    /**
     * Processes a private 1-on-1 chat message: persists it to MySQL, pushes it in real time
     * to both the recipient's and sender's user queues, and triggers an in-app notification.
     * <ul>
     *   <li><b>WebSocket Destination:</b> {@code /app/chat.private}</li>
     *   <li><b>Target Queues:</b> {@code /user/{receiverEmail}/queue/messages} and {@code /user/{senderEmail}/queue/messages}</li>
     *   <li><b>Access Role:</b> Any Authenticated User ({@code STUDENT}, {@code ALUMNI}, {@code ADMIN})</li>
     * </ul>
     *
     * @param message   the {@link ChatMessage} containing {@code receiverName} and {@code content}
     * @param principal the authenticated sender's WebSocket principal
     */
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

    /**
     * Forwards a real-time typing status indicator to the target conversation partner.
     * <ul>
     *   <li><b>WebSocket Destination:</b> {@code /app/chat.typing}</li>
     *   <li><b>Target Queue:</b> {@code /user/{receiverEmail}/queue/typing}</li>
     *   <li><b>Access Role:</b> Any Authenticated User</li>
     * </ul>
     *
     * @param message   the {@link TypingMessage} payload indicating whether the sender is typing
     * @param principal the authenticated sender's WebSocket principal
     */
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

    // =========================================================
    // 3. CHAT HISTORY & PARTNERS (REST API)
    // =========================================================

    /**
     * Fetches the chronological private message history between the currently logged-in user
     * and the specified conversation partner.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code GET /api/messages/history?partnerEmail={email}}</li>
     *   <li><b>Access Role:</b> Any Authenticated User ({@code STUDENT}, {@code ALUMNI}, {@code ADMIN})</li>
     *   <li><b>Query Parameter:</b> {@code partnerEmail} - email of the other user in the conversation</li>
     *   <li><b>Response:</b> {@code 200 OK} with list of {@link PrivateMessage}, or {@code 401 Unauthorized}</li>
     * </ul>
     *
     * @param partnerEmail the email address of the chat partner
     * @return a {@link ResponseEntity} containing the ordered list of messages
     */
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

    /**
     * Retrieves the list of unique users with whom the currently authenticated user
     * has exchanged private messages, ordered by most recent interaction.
     * <ul>
     *   <li><b>HTTP Route:</b> {@code GET /api/messages/partners}</li>
     *   <li><b>Access Role:</b> Any Authenticated User ({@code STUDENT}, {@code ALUMNI}, {@code ADMIN})</li>
     *   <li><b>Response:</b> {@code 200 OK} with list of {@link User} entities, or {@code 401 Unauthorized}</li>
     * </ul>
     *
     * @return a {@link ResponseEntity} containing the user's recent chat partners
     */
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