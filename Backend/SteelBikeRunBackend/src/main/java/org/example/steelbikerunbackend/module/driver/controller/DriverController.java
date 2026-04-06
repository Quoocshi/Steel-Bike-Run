package org.example.steelbikerunbackend.module.driver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.steelbikerunbackend.common.response.ApiResponse;
import org.example.steelbikerunbackend.module.driver.dto.DriverProfileResponse;
import org.example.steelbikerunbackend.module.driver.dto.SwitchDriverRequest;
import org.example.steelbikerunbackend.module.driver.service.DriverService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Driver", description = "Quản lý tài xế: kích hoạt chế độ Driver, xem profile")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/driver")
@RequiredArgsConstructor
public class DriverController {

        private final DriverService driverService;

        /**
         * Kích hoạt / tắt chế độ Driver.
         *
         * <ul>
         * <li><b>Lần đầu tiên</b>: bắt buộc gửi thông tin xe để tạo profile → tự động
         * set online.</li>
         * <li><b>Các lần sau</b>: body bị bỏ qua, chỉ toggle is_online (online ↔
         * offline).</li>
         * </ul>
         */
        @Operation(summary = "Kích hoạt / tắt chế độ Driver", description = """
                        **Lần đầu tiên** (chưa có profile Driver):
                        - Bắt buộc gửi `vehiclePlate`, `vehicleModel`, `vehicleColor`, `licenseNumber`.
                        - Hệ thống tạo profile Driver và đặt `isOnline = true`.
                        - Response trả về `isNewProfile = true`.

                        **Các lần sau** (đã có profile Driver):
                        - Body không cần thiết (bỏ qua).
                        - Hệ thống toggle `isOnline`: `true → false` hoặc `false → true`.
                        - Response trả về `isNewProfile = false`.
                        """)
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Toggle thành công / Profile đã tạo và online"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Thiếu thông tin xe (lần đầu) hoặc biển số/bằng lái trùng"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Tài khoản không có quyền DRIVER")
        })
        @PreAuthorize("hasRole('DRIVER')")
        @PostMapping("/switch")
        public ResponseEntity<ApiResponse<DriverProfileResponse>> switchDriver(
                        @AuthenticationPrincipal String userEmail,
                        @Valid @RequestBody(required = false) SwitchDriverRequest request) {

                DriverProfileResponse response = driverService.switchDriver(userEmail, request);

                String message = response.isNewProfile()
                                ? "Profile Driver đã được tạo. Trạng thái: Online"
                                : (response.isOnline() ? "Đã chuyển sang trạng thái: Online"
                                                : "Đã chuyển sang trạng thái: Offline");

                return ResponseEntity.ok(ApiResponse.success(message, response));
        }

        /**
         * Lấy profile Driver của tài xế đang đăng nhập.
         */
        @Operation(summary = "Lấy profile Driver", description = "Trả về thông tin profile Driver của tài xế hiện tại. Trả lỗi 400 nếu chưa tạo profile.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lấy profile thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Profile Driver chưa tồn tại")
        })
        @PreAuthorize("hasRole('DRIVER')")
        @GetMapping("/profile")
        public ResponseEntity<ApiResponse<DriverProfileResponse>> getMyProfile(
                        @AuthenticationPrincipal String userEmail) {

                DriverProfileResponse response = driverService.getMyProfile(userEmail);
                return ResponseEntity.ok(ApiResponse.success(response));
        }
}
