package org.example.steelbikerunbackend.module.driver.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Thông tin một tài xế gần nhất trả về cho Customer.
 * Được sort theo khoảng cách Haversine tăng dần.
 */
@Schema(description = "Thông tin tài xế gần nhất")
public record NearbyDriverResponse(

        @Schema(description = "ID tài xế", example = "a1b2c3d4-...")
        String driverId,

        @Schema(description = "Họ tên tài xế", example = "Nguyễn Văn A")
        String fullName,

        @Schema(description = "Biển số xe", example = "51G-12345")
        String vehiclePlate,

        @Schema(description = "Dòng xe", example = "Honda Wave Alpha")
        String vehicleModel,

        @Schema(description = "Màu xe", example = "Đen")
        String vehicleColor,

        @Schema(description = "Đánh giá trung bình (1–5)", example = "4.8")
        float rating,

        @Schema(description = "Vĩ độ hiện tại của tài xế", example = "10.7769")
        double latitude,

        @Schema(description = "Kinh độ hiện tại của tài xế", example = "106.7009")
        double longitude,

        @Schema(description = "H3 cell index của tài xế (resolution=9)", example = "891f1d4b2a3ffff")
        String h3Index,

        @Schema(description = "Khoảng cách tới điểm đón (km)", example = "1.23")
        double distanceKm,

        @Schema(description = "Hướng di chuyển của tài xế (0–360 độ)", example = "90.0")
        Float heading,

        @Schema(description = "Tốc độ di chuyển (km/h)", example = "25.0")
        Float speed
) {}
