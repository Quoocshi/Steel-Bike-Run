package org.example.steelbikerunbackend.module.driver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Request gửi lên từ app tài xế mỗi 3 giây (location heartbeat).
 * Chỉ DRIVER mới gọi được.
 */
public record LocationUpdateRequest(

        @Schema(description = "Vĩ độ hiện tại của tài xế", example = "10.7769")
        @NotNull(message = "Latitude không được để trống")
        @DecimalMin(value = "-90.0", message = "Latitude không hợp lệ")
        @DecimalMax(value = "90.0", message = "Latitude không hợp lệ")
        Double latitude,

        @Schema(description = "Kinh độ hiện tại của tài xế", example = "106.7009")
        @NotNull(message = "Longitude không được để trống")
        @DecimalMin(value = "-180.0", message = "Longitude không hợp lệ")
        @DecimalMax(value = "180.0", message = "Longitude không hợp lệ")
        Double longitude,

        @Schema(description = "Hướng di chuyển (0–360 độ, tùy chọn)", example = "90.0")
        Float heading,

        @Schema(description = "Tốc độ di chuyển (km/h, tùy chọn)", example = "30.5")
        Float speed
) {}
