package org.example.steelbikerunbackend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.steelbikerunbackend.common.security.JwtUtil;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * STOMP Channel Interceptor — xác thực JWT token từ STOMP CONNECT frame.
 *
 * <h3>Vấn đề:</h3>
 * JwtAuthFilter chỉ hoạt động cho HTTP requests thông thường.
 * WebSocket STOMP messages không đi qua servlet filter chain.
 * → Principal luôn null → LocationWebSocketHandler không hoạt động.
 *
 * <h3>Giải pháp:</h3>
 * Interceptor này đọc JWT từ header "Authorization" của STOMP CONNECT frame,
 * validate và set Principal cho session.
 *
 * <h3>Flow:</h3>
 * <pre>
 * 1. Client gửi CONNECT frame với header "Authorization: Bearer <token>"
 * 2. Interceptor đọc token, validate với JwtUtil
 * 3. Nếu hợp lệ → set Principal vào StompHeaderAccessor
 * 4. LocationWebSocketHandler nhận Principal không null → xử lý heartbeat
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompJwtChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            // Lấy Authorization header từ STOMP CONNECT frame
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("[STOMP Auth] CONNECT without Authorization header");
                return message;
            }

            String token = authHeader.substring(7); // Remove "Bearer " prefix

            if (jwtUtil.isTokenValid(token)) {
                String email = jwtUtil.extractEmail(token);
                String role = jwtUtil.extractRole(token);
                
                // Nếu role null, mặc định là CUSTOMER
                if (role == null) {
                    role = "CUSTOMER";
                    log.warn("[STOMP Auth] Token has no role claim, defaulting to CUSTOMER");
                }

                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                var authentication = new UsernamePasswordAuthenticationToken(email, null, authorities);

                // Set Principal vào STOMP session — Spring sẽ inject Principal này vào handler
                accessor.setUser(authentication);
                // Cũng set vào SecurityContext để các component khác có thể truy cập
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.info("[STOMP Auth] User {} connected with role {}", email, role);
            } else {
                log.warn("[STOMP Auth] Invalid JWT token in CONNECT frame");
            }
        }

        return message;
    }
}
