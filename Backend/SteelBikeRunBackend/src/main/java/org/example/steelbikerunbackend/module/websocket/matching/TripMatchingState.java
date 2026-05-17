package org.example.steelbikerunbackend.module.websocket.matching;

import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Trạng thái matching của một trip đang tìm tài xế, lưu trong Redis.
 *
 * <p>Vòng đời:
 * <pre>
 * createTrip() → MatchingEngine enqueue → round 1 broadcast → chờ 20s
 *            → tài xế không nhận → round 2 broadcast (tài xế khác) → ...
 *            → driver accept → remove from queue
 *            → quá 5 phút → CANCELLED
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripMatchingState implements Serializable {

    /** ID của cuốc xe (UUID dạng String). */
    private String tripId;

    /** ID khách hàng (UUID dạng String), dùng để gửi WebSocket. */
    private String customerId;

    /** Vĩ độ điểm đón. */
    private double pickupLat;

    /** Kinh độ điểm đón. */
    private double pickupLng;

    /** Thời điểm trip được tạo — dùng để tính global timeout. */
    private Instant createdAt;

    /**
     * Thời điểm round broadcast gần nhất.
     * Engine chỉ broadcast round mới sau khi round cũ hết thời gian chờ.
     */
    private Instant lastBroadcastAt;

    /** Round hiện tại (bắt đầu từ 1). */
    private int round;

    /**
     * Set driverId đã được broadcast hoặc đã từ chối.
     * Engine sẽ loại những driver này khỏi các round sau.
     */
    @Builder.Default
    private Set<String> rejectedOrNotifiedDriverIds = new HashSet<>();
}
