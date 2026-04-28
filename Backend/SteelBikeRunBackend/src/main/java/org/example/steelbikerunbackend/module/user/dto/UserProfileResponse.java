package org.example.steelbikerunbackend.module.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.example.steelbikerunbackend.common.enums.UserRole;
import org.example.steelbikerunbackend.module.user.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Thông tin profile người dùng")
public record UserProfileResponse(

        @Schema(description = "User ID")
        UUID id,

        @Schema(description = "Email")
        String email,

        @Schema(description = "Số điện thoại")
        String phone,

        @Schema(description = "Họ và tên")
        String fullName,

        @Schema(description = "URL ảnh đại diện")
        String avatarUrl,

        @Schema(description = "Vai trò")
        UserRole role,

        @Schema(description = "Tài khoản đang hoạt động")
        boolean isActive,

        @Schema(description = "Ngày tạo tài khoản")
        LocalDateTime createdAt
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
