package org.example.steelbikerunbackend.module.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.steelbikerunbackend.module.driver.dto.LocationUpdateRequest;
import org.example.steelbikerunbackend.module.driver.service.DriverLocationService;
import org.example.steelbikerunbackend.module.websocket.dto.LocationHeartbeat;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * LocationWebSocketHandler — nhận heartbeat vị trí từ Driver qua WebSocket.
 *
 * <h3>Tại sao dùng WebSocket thay vì REST cho heartbeat?</h3>
 * <ul>
 *   <li>REST mỗi 3s = 1 HTTP handshake + header ~1KB mỗi lần → lãng phí.</li>
 *   <li>WebSocket = kết nối persist, gửi payload nhỏ ~50 bytes, không overhead.</li>
 *   <li>Server có thể push ngược lại vị trí tài xế cho customer ngay lập tức.</li>
 * </ul>
 *
 * <h3>Endpoint:</h3>
 * <pre>
 * Client gửi tới: /app/driver.location
 * Mapped tới:     @MessageMapping("/driver.location")
 * </pre>
 *
 * <h3>Bảo mật:</h3>
 * <p>Principal được inject từ STOMP CONNECT frame (JWT token trong header).
 * Cấu hình xác thực JWT qua WebSocket sẽ được thêm ở Tuần 5 (security review).
 * Hiện tại dùng email từ Principal hoặc fallback từ header.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class LocationWebSocketHandler {

    private final DriverLocationService driverLocationService;

    /**
     * Nhận heartbeat vị trí từ Driver qua WebSocket STOMP.
     *
     * <p>Driver app gửi message đến /app/driver.location mỗi 3 giây:
     * <pre>
     * {
     *   "latitude": 10.7769,
     *   "longitude": 106.7009,
     *   "heading": 90.0,
     *   "speed": 25.5
     * }
     * </pre>
     *
     * <p>Handler gọi lại DriverLocationService.updateLocation() — cùng logic với REST API
     * nhưng không có HTTP overhead. Kết quả ghi vào Redis (TTL 60s).
     *
     * @param heartbeat  payload JSON từ driver
     * @param principal  user hiện tại (lấy từ STOMP CONNECT)
     * @param headerAccessor để lấy session info khi cần debug
     */
    @MessageMapping("/driver.location")
    public void handleLocationHeartbeat(
            LocationHeartbeat heartbeat,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor) {

        if (principal == null) {
            log.warn("[WS Location] Nhận heartbeat nhưng không có Principal (chưa xác thực). Session: {}",
                    headerAccessor.getSessionId());
            return;
        }

        String driverEmail = principal.getName();

        // Tái sử dụng LocationUpdateRequest — cùng logic với REST API /location
        LocationUpdateRequest request = new LocationUpdateRequest(
                heartbeat.getLatitude(),
                heartbeat.getLongitude(),
                heartbeat.getHeading(),
                heartbeat.getSpeed()
        );

        try {
            driverLocationService.updateLocation(driverEmail, request);
            log.debug("[WS Location] Driver [{}] -> lat={}, lng={}",
                    driverEmail, heartbeat.getLatitude(), heartbeat.getLongitude());
        } catch (Exception e) {
            // Không throw exception ở WebSocket handler — chỉ log để tránh disconnect session
            log.error("[WS Location] Lỗi khi cập nhật vị trí cho driver [{}]: {}",
                    driverEmail, e.getMessage());
        }
    }
}
