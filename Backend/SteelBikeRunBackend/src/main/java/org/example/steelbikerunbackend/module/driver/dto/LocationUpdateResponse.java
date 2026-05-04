package org.example.steelbikerunbackend.module.driver.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Response trả về sau khi cập nhật vị trí thành công.
 * Bao gồm H3 cell index để mobile có thể vẽ overlay.
 */
public record LocationUpdateResponse(

        @Schema(description = "ID tài xế")
        String driverId,

        @Schema(description = "Vĩ độ đã lưu", example = "10.7769")
        double latitude,

        @Schema(description = "Kinh độ đã lưu", example = "106.7009")
        double longitude,

        @Schema(description = "H3 cell index (resolution=9) — dùng cho overlay bản đồ",
                example = "891f1d4b2a3ffff")
        String h3Index,

        @Schema(description = "Thời điểm cập nhật")
        Instant updatedAt
) {}
