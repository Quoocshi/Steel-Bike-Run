package org.example.steelbikerunbackend.module.websocket.dto;

import lombok.*;

/**
 * Server -> Customer/Driver: cập nhật trạng thái cuốc xe.
 * Gửi qua kênh /topic/trip/{customerId} hoặc /topic/driver/{driverId}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripStatusMessage {

    private String tripId;
    private String status;    // REQUESTED, ACCEPTED, IN_PROGRESS, COMPLETED, CANCELLED
    private String message;   // "Tài xế đang đến", "Đã bắt đầu chuyến đi", ...
    private String timestamp;
}
