package org.example.steelbikerunbackend.module.trip.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Cache vùng surge pricing theo ô H3.
 *
 * <p>PK = h3_index để UPSERT nhanh (O(1)). Giá được tính trước mỗi 5 phút
 * bởi một batch job, không phải real-time, để tránh tốn CPU mỗi request.
 */
@Entity
@Table(name = "h3_surge_zones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class H3SurgeZone {

    @Id
    @Column(name = "h3_index", length = 20)
    private String h3Index;

    // 1.0 = giá bình thường, 2.0 = gấp đôi
    @Column(name = "surge_multiplier", nullable = false)
    @Builder.Default
    private float surgeMultiplier = 1.0f;

    @Column(name = "active_drivers", nullable = false)
    @Builder.Default
    private int activeDrivers = 0;

    @Column(name = "pending_trips", nullable = false)
    @Builder.Default
    private int pendingTrips = 0;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        calculatedAt = LocalDateTime.now();
    }
}
