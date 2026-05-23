package com.irons.schismbackend.config;

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
        // This is the initial connection URL your Android app will target to open the tunnel.
        // We allow all origins (*) so your Android Emulator or physical phone can connect seamlessly.
        registry.addEndpoint("/schism-connection")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // This defines the prefix for messages traveling from SERVER -> CLIENT
        registry.enableSimpleBroker("/queue", "/topic");
        // Inbound channels: Destinations where the Android client sends data (e.g., /app/interrogate)
        registry.setApplicationDestinationPrefixes("/app");
        // Outbound channels: The broker prefix the backend uses to stream events BACK to the phones.
        // /queue is for private 1v1 data (turns, leaks), /topic can be for system-wide updates.
        registry.enableSimpleBroker("/queue", "/topic");
        // This targets specific user queues so Player A's data doesn't accidentally leak to Player B.
        registry.setUserDestinationPrefix("/user");
    }
}
