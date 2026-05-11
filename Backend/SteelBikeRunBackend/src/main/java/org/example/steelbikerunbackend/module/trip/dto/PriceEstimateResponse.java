package org.example.steelbikerunbackend.module.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Kết quả ước tính giá — trả về trước khi customer xác nhận đặt xe.
 * Mobile dùng để hiển thị preview giá trên màn hình BookingScreen.
 */
@Schema(description = "Kết quả ước tính giá chuyến đi")
public record PriceEstimateResponse(

        @Schema(description = "H3 cell index của điểm đón (resolution=9)", example = "891f1d4b2a3ffff")
        String pickupH3Index,

        @Schema(description = "Khoảng cách ước tính (km) theo đường chim bay x hệ số đường",
                example = "5.23")
        double distanceKm,

        @Schema(description = "Thời gian ước tính (phút)", example = "18")
        int durationMinutes,

        @Schema(description = "Giá cơ bản trước surge (VNĐ)", example = "35000")
        BigDecimal basePrice,

        @Schema(description = "Hệ số surge hiện tại (1.0 = bình thường, 1.5 = x1.5)",
                example = "1.5")
        BigDecimal surgeMultiplier,

        @Schema(description = "Giá cuối sau surge = basePrice × surgeMultiplier (VNĐ)",
                example = "52500")
        BigDecimal finalPrice,

        @Schema(description = "Có đang surge pricing không", example = "true")
        boolean isSurging
) {}
