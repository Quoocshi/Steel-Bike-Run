package org.example.steelbikerunbackend.module.driver.cache;

import lombok.*;

import java.io.Serializable;
import java.time.Instant;

/**
 * POJO đại diện cho dữ liệu vị trí tài xế lưu trong Redis.
 *
 * <p>Key schema: {@code driver:location:{driverId}} → HASH
 * <br>TTL: 60 giây — nếu driver mất kết nối > 60s, key tự động bị xóa.
 *
 * <p>Tại sao dùng HASH thay vì String (JSON)?
 * → HGET field riêng lẻ mà không cần deserialize toàn bộ object.
 * → Tiết kiệm memory hơn JSON.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverLocationCache implements Serializable {

    private String driverId;

    private double latitude;
    private double longitude;

    // H3 cell index (resolution=9)
    private String h3Index;

    // Hướng di chuyển (0–360 độ)
    private Float heading;

    // Tốc độ (km/h)
    private Float speed;

    // true nếu tài xế đang online và gửi heartbeat
    private boolean isOnline;

    // Thời điểm cập nhật gần nhất (dùng để biết data có stale không)
    private Instant updatedAt;
}
