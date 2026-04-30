package org.example.steelbikerunbackend.module.driver.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response trả về khi switch role, bao gồm JWT mới với role đã cập nhật")
public record SwitchRoleResponse(

        @Schema(description = "JWT mới mang role đã cập nhật. Client phải lưu đè token cũ ngay sau khi nhận.") String accessToken,

        @Schema(description = "Profile Driver sau khi switch") DriverProfileResponse driverProfile) {
    public static SwitchRoleResponse of(String accessToken, DriverProfileResponse driverProfile) {
        return new SwitchRoleResponse(accessToken, driverProfile);
    }
}
