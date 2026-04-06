package org.example.steelbikerunbackend.module.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.example.steelbikerunbackend.common.enums.UserRole;

@Schema(description = "Request body để đăng ký tài khoản mới")
public record RegisterRequest(

        @Schema(description = "Email", example = "user@example.com")
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không hợp lệ")
        String email,

        @Schema(description = "Số điện thoại (10 chữ số)", example = "0912345678")
        @NotBlank(message = "Số điện thoại không được để trống")
        @Pattern(regexp = "^0[0-9]{9}$", message = "Số điện thoại không hợp lệ")
        String phone,

        @Schema(description = "Mật khẩu", example = "password123")
        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 6, message = "Mật khẩu tối thiểu 6 ký tự")
        String password,

        @Schema(description = "Họ và tên", example = "Nguyễn Văn A")
        @NotBlank(message = "Họ tên không được để trống")
        String fullName,

        @Schema(description = "Vai trò: CUSTOMER hoặc DRIVER", example = "CUSTOMER")
        UserRole role
) {}
