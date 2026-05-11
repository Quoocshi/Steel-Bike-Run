package org.example.steelbikerunbackend.module.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request ước tính giá chuyến đi (không cần đặt xe ngay).
 * Cũng dùng cho request tạo chuyến xe thật.
 */
@Schema(description = "Thông tin điểm đón và điểm đến để ước tính giá")
public record PriceEstimateRequest(

        @NotNull(message = "pickupLat không được để trống")
        @DecimalMin(value = "-90.0", message = "Vĩ độ phải >= -90")
        @DecimalMax(value = "90.0",  message = "Vĩ độ phải <= 90")
        @Schema(description = "Vĩ độ điểm đón", example = "10.7769")
        Double pickupLat,

        @NotNull(message = "pickupLng không được để trống")
        @DecimalMin(value = "-180.0", message = "Kinh độ phải >= -180")
        @DecimalMax(value = "180.0",  message = "Kinh độ phải <= 180")
        @Schema(description = "Kinh độ điểm đón", example = "106.7009")
        Double pickupLng,

        @NotNull(message = "destLat không được để trống")
        @DecimalMin(value = "-90.0", message = "Vĩ độ phải >= -90")
        @DecimalMax(value = "90.0",  message = "Vĩ độ phải <= 90")
        @Schema(description = "Vĩ độ điểm đến", example = "10.8230")
        Double destLat,

        @NotNull(message = "destLng không được để trống")
        @DecimalMin(value = "-180.0", message = "Kinh độ phải >= -180")
        @DecimalMax(value = "180.0",  message = "Kinh độ phải <= 180")
        @Schema(description = "Kinh độ điểm đến", example = "106.6297")
        Double destLng,

        @NotBlank(message = "destAddress không được để trống")
        @Schema(description = "Địa chỉ điểm đến (hiển thị cho driver)", example = "Sân bay Tân Sơn Nhất")
        String destAddress
) {}
