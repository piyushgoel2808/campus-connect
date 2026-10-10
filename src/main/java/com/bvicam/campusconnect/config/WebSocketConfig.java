package com.bvicam.campusconnect.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configures real-time bidirectional communication using STOMP over WebSockets (with SockJS fallback).
 *
 * <p><b>Architectural Role:</b></p>
 * <ul>
 *   <li>Powers real-time 1-on-1 private chat, typing indicators ({@code ChatController}), and
 *       instant in-app notifications ({@code NotificationService}).</li>
 *   <li>Exposes {@code /ws} as the SockJS handshake endpoint so browsers can establish persistent
 *       connections even behind restrictive proxies.</li>
 *   <li>Configures an in-memory message broker routing messages to {@code /topic}, {@code /queue},
 *       and {@code /user} destinations.</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Registers the {@code /ws} STOMP WebSocket handshake endpoint with SockJS fallback enabled.
     *
     * @param registry the STOMP endpoint registry
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // ✅ The main endpoint for connection
        // setAllowedOriginPatterns("*") is critical for deployment (Render/Heroku)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    /**
     * Configures the STOMP message broker routing prefixes for client subscriptions and server-bound messages.
     *
     * <p><b>Routing Summary:</b></p>
     * <ul>
     *   <li>{@code /topic/**}: Public broadcast channels (e.g., {@code /topic/messages/{email}},
     *       {@code /topic/typing/{email}}, {@code /topic/notifications/{email}}).</li>
     *   <li>{@code /queue/**} &amp; {@code /user/**}: Point-to-point and user-scoped destinations.</li>
     *   <li>{@code /app/**}: Prefix for messages sent <i>from</i> the client <i>to</i> server
     *       {@code @MessageMapping} controller methods (e.g., {@code /app/chat}, {@code /app/typing}).</li>
     * </ul>
     *
     * @param config the message broker registry
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 1. Destinations the client can SUBSCRIBE to
        // /topic -> Public/Broadcast
        // /queue -> Private messages (standard convention)
        // /user  -> User-specific events
        config.enableSimpleBroker("/topic", "/queue", "/user");

        // 2. Destinations the client SENDS to (mapped to @MessageMapping in Controllers)
        config.setApplicationDestinationPrefixes("/app");

        // 3. Prefix for User-specific messaging (e.g. /user/queue/messages)
        config.setUserDestinationPrefix("/user");
    }
}