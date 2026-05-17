package org.example.steelbikerunbackend.module.websocket.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * Server -> Driver: thông báo có cuốc xe mới cần nhận.
 * Gửi qua kênh /topic/driver/{driverId}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripRequestMessage {

    private String tripId;
    private String customerId;
    private String customerName;
    private String customerPhone;

    // Điểm đón
    private double pickupLat;
    private double pickupLng;
    private String pickupH3Index;

    // Điểm đến
    private double destLat;
    private double destLng;
    private String destAddress;

    // Giá
    private BigDecimal finalPrice;
    private BigDecimal surgeMultiplier;

    // Khoảng cách từ driver đến điểm đón (km)
    private double distanceToPickupKm;

    // Thời gian tối đa để driver phản hồi (giây)
    private int timeoutSeconds;
}
