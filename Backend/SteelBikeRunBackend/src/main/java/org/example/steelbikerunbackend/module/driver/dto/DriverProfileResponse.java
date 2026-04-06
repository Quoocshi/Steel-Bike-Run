package org.example.steelbikerunbackend.module.driver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.example.steelbikerunbackend.module.driver.entity.Driver;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Thông tin profile tài xế")
public record DriverProfileResponse(

        @Schema(description = "Driver profile ID")
        UUID driverId,

        @Schema(description = "User ID liên kết")
        UUID userId,

        @Schema(description = "Họ tên tài xế")
        String fullName,

        @Schema(description = "Email")
        String email,

        @Schema(description = "Số điện thoại")
        String phone,

        @Schema(description = "Ảnh đại diện")
        String avatarUrl,

        @Schema(description = "Biển số xe")
        String vehiclePlate,

        @Schema(description = "Hãng và dòng xe")
        String vehicleModel,

        @Schema(description = "Màu xe")
        String vehicleColor,

        @Schema(description = "Số bằng lái")
        String licenseNumber,

        @Schema(description = "Trạng thái online")
        boolean isOnline,

        @Schema(description = "Điểm đánh giá trung bình")
        float rating,

        @Schema(description = "Tổng số chuyến hoàn thành")
        int totalTrips,

        @Schema(description = "Tài xế đã qua kiểm tra độ tỉnh táo")
        boolean faceScanPassed,

        @Schema(description = "Lần quét mặt gần nhất")
        LocalDateTime lastFaceScanAt,

        @Schema(description = "Profile là mới tạo hay đã tồn tại trước đó")
        boolean isNewProfile
) {
    /**
     * Map từ Driver entity sang DriverProfileResponse.
     *
     * @param driver     Driver entity (phải có user đã fetch)
     * @param isNew      true nếu vừa được tạo lần đầu trong lần switch này
     */
    public static DriverProfileResponse from(Driver driver, boolean isNew) {
        return new DriverProfileResponse(
                driver.getId(),
                driver.getUser().getId(),
                driver.getUser().getFullName(),
                driver.getUser().getEmail(),
                driver.getUser().getPhone(),
                driver.getUser().getAvatarUrl(),
                driver.getVehiclePlate(),
                driver.getVehicleModel(),
                driver.getVehicleColor(),
                driver.getLicenseNumber(),
                driver.isOnline(),
                driver.getRating(),
                driver.getTotalTrips(),
                driver.isFaceScanPassed(),
                driver.getLastFaceScanAt(),
                isNew
        );
    }
}
