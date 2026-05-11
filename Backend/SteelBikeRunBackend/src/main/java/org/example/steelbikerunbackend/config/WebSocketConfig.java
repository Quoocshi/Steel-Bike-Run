package org.example.steelbikerunbackend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Cấu hình WebSocket STOMP cho hệ thống matching realtime.
 *
 * <h3>Kiến trúc kênh:</h3>
 * <pre>
 * Mobile App gửi message   -> /app/...       (Application destination prefix)
 * Server broadcast đến app -> /topic/...     (Broker destination prefix)
 * Server gửi cho 1 user    -> /queue/...     (User-specific queue)
 * </pre>
 *
 * <h3>Endpoints chính:</h3>
 * <ul>
 *   <li>Customer subscribe: /topic/trip/{customerId} -> nhận DriverFoundMessage, StatusUpdate</li>
 *   <li>Driver subscribe: /topic/driver/{driverId} -> nhận NewTripRequest</li>
 *   <li>Driver gửi: /app/driver.location -> LocationHeartbeat</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Client subscribe vào /topic/* và /queue/* để nhận message
        config.enableSimpleBroker("/topic", "/queue");
        // Client gửi message đến /app/* -> route đến @MessageMapping
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint chính: ws://host:8081/ws
        // withSockJS() cho phép fallback khi browser/mobile không hỗ trợ WebSocket thuần
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // Endpoint thuần WebSocket (không SockJS) cho mobile client
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }
}
