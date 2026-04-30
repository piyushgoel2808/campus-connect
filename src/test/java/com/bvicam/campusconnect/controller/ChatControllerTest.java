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

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sendPrivateMessageUsesAuthenticatedSender() {
        ChatMessage payload = new ChatMessage();
        payload.setSenderName("client-ignored@example.com");
        payload.setReceiverName("receiver@example.com");
        payload.setContent("Hello");

        Principal principal = () -> "sender@example.com";

        chatController.sendPrivateMessage(payload, principal);

        ArgumentCaptor<PrivateMessage> messageCaptor = ArgumentCaptor.forClass(PrivateMessage.class);
        verify(messageRepo).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getSenderEmail()).isEqualTo("sender@example.com");
        assertThat(messageCaptor.getValue().getReceiverEmail()).isEqualTo("receiver@example.com");
        verify(messagingTemplate, times(2)).convertAndSendToUser(any(), any(), any());
        assertThat(payload.getSenderName()).isEqualTo("sender@example.com");
    }

    @Test
    void getHistoryReturnsUnauthorizedWithoutAuthentication() {
        ResponseEntity<List<PrivateMessage>> response = chatController.getHistory("partner@example.com");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }
}