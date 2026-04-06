package org.example.steelbikerunbackend.module.driver.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.steelbikerunbackend.module.user.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "drivers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "vehicle_plate", nullable = false, unique = true, length = 20)
    private String vehiclePlate;

    @Column(name = "vehicle_model", nullable = false, length = 100)
    private String vehicleModel;

    @Column(name = "vehicle_color", nullable = false, length = 50)
    private String vehicleColor;

    @Column(name = "license_number", nullable = false, unique = true, length = 50)
    private String licenseNumber;

    @Column(name = "is_online", nullable = false)
    @Builder.Default
    private boolean isOnline = false;

    @Column(nullable = false)
    @Builder.Default
    private float rating = 5.0f;

    @Column(name = "total_trips", nullable = false)
    @Builder.Default
    private int totalTrips = 0;

    @Column(name = "last_face_scan_at")
    private LocalDateTime lastFaceScanAt;

    @Column(name = "face_scan_passed", nullable = false)
    @Builder.Default
    private boolean faceScanPassed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
