package org.example.steelbikerunbackend.module.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.steelbikerunbackend.common.response.ApiResponse;
import org.example.steelbikerunbackend.module.auth.dto.AuthResponse;
import org.example.steelbikerunbackend.module.auth.dto.LoginRequest;
import org.example.steelbikerunbackend.module.auth.dto.RegisterRequest;
import org.example.steelbikerunbackend.module.auth.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Xác thực người dùng: đăng ký, đăng nhập, đăng xuất")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Đăng ký tài khoản",
            description = "Tạo tài khoản mới với role CUSTOMER hoặc DRIVER"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Đăng ký thành công, trả về JWT token"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email hoặc số điện thoại đã tồn tại")
    })
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Đăng ký thành công", response));
    }

    @Operation(
            summary = "Đăng nhập",
            description = "Đăng nhập bằng email hoặc số điện thoại + mật khẩu. Trả về JWT access token."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đăng nhập thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Sai thông tin đăng nhập")
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", response));
    }

    @Operation(
            summary = "Đăng xuất",
            description = "Xóa JWT, set driver offline và xóa Redis cache. Đảm bảo driver không bị stale trong hệ thống sau khi đăng xuất."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đăng xuất thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal String userEmail) {
        authService.logout(userEmail);
        return ResponseEntity.ok(ApiResponse.success("Đăng xuất thành công", null));
    }
}
