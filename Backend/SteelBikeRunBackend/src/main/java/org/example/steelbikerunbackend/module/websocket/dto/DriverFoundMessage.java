package org.example.steelbikerunbackend.module.websocket.dto;

import lombok.*;

/**
 * Server -> Customer: thông báo đã tìm được tài xế.
 * Gửi qua kênh /topic/trip/{customerId}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverFoundMessage {

    private String tripId;
    private String driverId;
    private String driverName;
    private String driverPhone;
    private int driverTotalTrips;

    // Thông tin xe
    private String vehiclePlate;
    private String vehicleModel;
    private String vehicleColor;

    // Vị trí hiện tại của tài xế
    private double driverLat;
    private double driverLng;

    // Rating tài xế
    private float driverRating;

    // ETA ước tính đến điểm đón (phút)
    private int etaMinutes;
}
