package org.example.steelbikerunbackend.module.driver.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Vị trí tài xế lưu trong PostgreSQL — đây là lớp PERSISTENCE,
 * KHÔNG phải primary store (primary store là Redis).
 * Bảng này được sync từ Redis mỗi 30 giây bởi
 * {@link org.example.steelbikerunbackend.module.driver.service.DriverLocationSyncJob}.
 */
@Entity
@Table(name = "driver_locations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Quan hệ 1-1 với Driver (mỗi tài xế chỉ có 1 bản ghi location)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false, unique = true)
    private Driver driver;

    // H3 cell index (resolution=9, ~174m hexagon) — dùng cho analytics và surge
    // pricing
    @Column(name = "h3_index", nullable = false, length = 20)
    private String h3Index;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    // Hướng di chuyển (0–360 độ)
    @Column
    private Float heading;

    // Tốc độ (km/h)
    @Column
    private Float speed;

    // Timestamp của lần sync gần nhất từ Redis
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    private void setUpdatedAt() {
        this.updatedAt = Instant.now();
    }
}
