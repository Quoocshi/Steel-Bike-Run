package org.example.steelbikerunbackend.module.trip.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.steelbikerunbackend.common.enums.TripStatus;
import org.example.steelbikerunbackend.module.driver.entity.Driver;
import org.example.steelbikerunbackend.module.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity cuốc xe — bản ghi chính của toàn bộ vòng đời một chuyến đi.
 *
 * <p><b>Lưu ý thiết kế:</b>
 * <ul>
 *   <li>{@code surgeMultiplier} là snapshot giá tại thời điểm đặt.
 *       Giá không bao giờ thay đổi sau khi trip được tạo.</li>
 *   <li>{@code driverId} là nullable cho đến khi driver accept.</li>
 *   <li>Các {@code *_at} timestamps là audit trail đầy đủ.</li>
 * </ul>
 */
@Entity
@Table(name = "trips")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    // Nullable cho đến khi có driver accept
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Column(name = "pickup_lat", nullable = false)
    private double pickupLat;

    @Column(name = "pickup_lng", nullable = false)
    private double pickupLng;

    @Column(name = "pickup_h3_index", nullable = false, length = 20)
    private String pickupH3Index;

    @Column(name = "dest_lat", nullable = false)
    private double destLat;

    @Column(name = "dest_lng", nullable = false)
    private double destLng;

    @Column(name = "dest_address", nullable = false, length = 500)
    private String destAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TripStatus status = TripStatus.REQUESTED;

    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    // Snapshot của surge multiplier tại thời điểm đặt xe — KHÔNG thay đổi sau này
    @Column(name = "surge_multiplier", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal surgeMultiplier = BigDecimal.ONE;

    @Column(name = "final_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal finalPrice;

    @Column(name = "distance_km", nullable = false)
    private float distanceKm;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        requestedAt = LocalDateTime.now();
    }
}
