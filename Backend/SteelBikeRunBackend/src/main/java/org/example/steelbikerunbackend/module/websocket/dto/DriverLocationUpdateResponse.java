package org.example.steelbikerunbackend.module.websocket.dto;

import lombok.*;

/**
 * Server -> Driver: phản hồi sau mỗi heartbeat thành công, chứa h3Index.
 * Driver app subscribe /topic/driver/{driverId}/location-update để nhận.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverLocationUpdateResponse {

    private String driverId;
    private double latitude;
    private double longitude;
    private String h3Index;
    private String updatedAt;
}
