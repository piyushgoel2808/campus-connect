package com.bvicam.campusconnect.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // ✅ The main endpoint for connection
        // setAllowedOriginPatterns("*") is critical for deployment (Render/Heroku)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

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