package org.example.steelbikerunbackend.module.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.example.steelbikerunbackend.common.enums.TripStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response trả về thông tin đầy đủ của một cuốc xe.
 * Dùng cho cả tạo trip, xem chi tiết, và lịch sử.
 */
@Schema(description = "Thông tin chi tiết cuốc xe")
public record TripResponse(

        @Schema(description = "ID cuốc xe")
        String id,

        @Schema(description = "ID khách hàng")
        String customerId,

        @Schema(description = "ID tài xế (null nếu chưa có)")
        String driverId,

        @Schema(description = "Tên tài xế (null nếu chưa có)")
        String driverName,

        // Điểm đón
        @Schema(description = "Vĩ độ điểm đón")
        double pickupLat,

        @Schema(description = "Kinh độ điểm đón")
        double pickupLng,

        @Schema(description = "H3 cell index điểm đón")
        String pickupH3Index,

        // Điểm đến
        @Schema(description = "Vĩ độ điểm đến")
        double destLat,

        @Schema(description = "Kinh độ điểm đến")
        double destLng,

        @Schema(description = "Địa chỉ điểm đến")
        String destAddress,

        // Giá
        @Schema(description = "Trạng thái cuốc xe")
        TripStatus status,

        @Schema(description = "Giá cơ bản (VNĐ)")
        BigDecimal basePrice,

        @Schema(description = "Hệ số surge")
        BigDecimal surgeMultiplier,

        @Schema(description = "Giá cuối (VNĐ)")
        BigDecimal finalPrice,

        @Schema(description = "Khoảng cách ước tính (km)")
        float distanceKm,

        @Schema(description = "Thời gian ước tính (phút)")
        int durationMinutes,

        // Timestamps
        @Schema(description = "Thời điểm đặt xe")
        LocalDateTime requestedAt,

        @Schema(description = "Thời điểm tài xế nhận cuốc")
        LocalDateTime acceptedAt,

        @Schema(description = "Thời điểm bắt đầu chuyến đi")
        LocalDateTime startedAt,

        @Schema(description = "Thời điểm hoàn thành")
        LocalDateTime completedAt
) {}
