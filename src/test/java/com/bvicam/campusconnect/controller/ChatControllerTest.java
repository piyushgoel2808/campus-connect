package com.bvicam.campusconnect.controller;

import com.bvicam.campusconnect.dto.ChatMessage;
import com.bvicam.campusconnect.entity.PrivateMessage;
import com.bvicam.campusconnect.repository.PrivateMessageRepository;
import com.bvicam.campusconnect.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link ChatController}.
 *
 * <p>Verifies WebSocket STOMP private messaging behavior and REST chat history security:
 * <ul>
 *   <li>Ensures private messages always use the authenticated {@link Principal} email as the sender
 *       rather than trusting the client-supplied {@code senderName} field.</li>
 *   <li>Ensures {@code GET /api/messages/history} returns HTTP 401 when unauthenticated.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private PrivateMessageRepository messageRepo;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ChatController chatController;

    /**
     * Clears the Spring Security context after each test to prevent state leakage between tests.
     */
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Verifies that {@link ChatController#sendPrivateMessage} overrides any spoofed client
     * {@code senderName} with the authenticated {@link Principal}'s email address, persists
     * the {@link PrivateMessage}, and dispatches STOMP frames to both the receiver and sender.
     */
    @Test
    void sendPrivateMessageUsesAuthenticatedSender() {
        // 1. Arrange: create a payload with a mismatched client senderName
        ChatMessage payload = new ChatMessage();
        payload.setSenderName("client-ignored@example.com");
        payload.setReceiverName("receiver@example.com");
        payload.setContent("Hello");

        Principal principal = () -> "sender@example.com";

        // 2. Act: send the private message through the STOMP handler
        chatController.sendPrivateMessage(payload, principal);

        // 3. Assert: persisted entity and STOMP payload use the authenticated principal's email
        ArgumentCaptor<PrivateMessage> messageCaptor = ArgumentCaptor.forClass(PrivateMessage.class);
        verify(messageRepo).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getSenderEmail()).isEqualTo("sender@example.com");
        assertThat(messageCaptor.getValue().getReceiverEmail()).isEqualTo("receiver@example.com");
        verify(messagingTemplate, times(2)).convertAndSendToUser(any(), any(), any());
        assertThat(payload.getSenderName()).isEqualTo("sender@example.com");
    }

    /**
     * Verifies that requesting conversation history without an authenticated session returns 401 Unauthorized.
     */
    @Test
    void getHistoryReturnsUnauthorizedWithoutAuthentication() {
        ResponseEntity<List<PrivateMessage>> response = chatController.getHistory("partner@example.com");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }
}