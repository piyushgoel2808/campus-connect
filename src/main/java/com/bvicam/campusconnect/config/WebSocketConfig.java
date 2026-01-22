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
        // ✅ ALLOW ALL ORIGINS (Fixes the connection issue on Render)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // "/topic" -> Public messages (Community Chat)
        // "/user"  -> Private messages (Direct Chat)
        registry.enableSimpleBroker("/topic", "/user");

        // Messages sent FROM the client must start with "/app"
        registry.setApplicationDestinationPrefixes("/app");
    }
}