package org.example.steelbikerunbackend.module.websocket.dto;

import lombok.*;

/**
 * Driver -> Server: gói tin heartbeat gửi qua WebSocket.
 * Client gửi đến /app/driver.location mỗi 3 giây.
 *
 * <p>Dùng class thay vì record vì STOMP deserialization cần no-arg constructor.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationHeartbeat {

    private double latitude;
    private double longitude;
    private Float heading;   // 0–360 độ, nullable
    private Float speed;     // km/h, nullable
}
