package org.example.steelbikerunbackend.module.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body khi customer bấm "Đặt xe" — tạo cuốc xe thật và bắt đầu matching.
 */
@Schema(description = "Thông tin đặt xe: tọa độ điểm đón, điểm đến")
public record CreateTripRequest(

        @NotNull(message = "pickupLat không được để trống")
        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        @Schema(description = "Vĩ độ điểm đón", example = "10.7769")
        Double pickupLat,

        @NotNull(message = "pickupLng không được để trống")
        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        @Schema(description = "Kinh độ điểm đón", example = "106.7009")
        Double pickupLng,

        @NotNull(message = "destLat không được để trống")
        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        @Schema(description = "Vĩ độ điểm đến", example = "10.8230")
        Double destLat,

        @NotNull(message = "destLng không được để trống")
        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        @Schema(description = "Kinh độ điểm đến", example = "106.6297")
        Double destLng,

        @NotBlank(message = "destAddress không được để trống")
        @Schema(description = "Địa chỉ điểm đến", example = "Sân bay Tân Sơn Nhất")
        String destAddress
) {}
