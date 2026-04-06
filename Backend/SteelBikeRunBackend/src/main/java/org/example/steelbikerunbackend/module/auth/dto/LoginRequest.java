package org.example.steelbikerunbackend.module.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body để đăng nhập")
public record LoginRequest(

        @Schema(description = "Email hoặc số điện thoại", example = "driver@example.com")
        @NotBlank(message = "Email hoặc số điện thoại không được để trống")
        String identifier,

        @Schema(description = "Mật khẩu", example = "password123")
        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 6, message = "Mật khẩu tối thiểu 6 ký tự")
        String password
) {}
