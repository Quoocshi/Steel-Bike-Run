package org.example.steelbikerunbackend.module.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.example.steelbikerunbackend.common.enums.UserRole;

import java.util.UUID;

@Schema(description = "Response sau khi đăng nhập / đăng ký thành công")
public record AuthResponse(

        @Schema(description = "JWT access token")
        String accessToken,

        @Schema(description = "Token type", example = "Bearer")
        String tokenType,

        @Schema(description = "User ID")
        UUID userId,

        @Schema(description = "Họ và tên")
        String fullName,

        @Schema(description = "Email")
        String email,

        @Schema(description = "Vai trò")
        UserRole role
) {
    public static AuthResponse of(String token, UUID userId, String fullName, String email, UserRole role) {
        return new AuthResponse(token, "Bearer", userId, fullName, email, role);
    }
}
