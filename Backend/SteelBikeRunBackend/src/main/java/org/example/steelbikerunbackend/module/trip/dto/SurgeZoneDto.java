package org.example.steelbikerunbackend.module.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Dữ liệu một ô H3 surge zone trả về cho mobile.
 *
 * @param h3Index        ô H3 (resolution=9)
 * @param centerLat      vĩ độ tâm ô
 * @param centerLng      kinh độ tâm ô
 * @param surgeMultiplier hệ số giá (1.0 = bình thường, >1.0 = đang surge)
 * @param activeDrivers  số tài xế đang hoạt động trong ô
 */
@Schema(description = "Thông tin một vùng surge pricing trên bản đồ")
public record SurgeZoneDto(
        @Schema(description = "H3 cell index (resolution=9)")
        String h3Index,

        @Schema(description = "Vĩ độ tâm ô")
        double centerLat,

        @Schema(description = "Kinh độ tâm ô")
        double centerLng,

        @Schema(description = "Hệ số surge (1.0 = bình thường)")
        float surgeMultiplier,

        @Schema(description = "Số tài xế đang hoạt động")
        int activeDrivers
) {}
