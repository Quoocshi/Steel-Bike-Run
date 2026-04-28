package org.example.steelbikerunbackend.module.driver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.steelbikerunbackend.common.response.ApiResponse;
import org.example.steelbikerunbackend.module.driver.dto.DriverProfileResponse;
import org.example.steelbikerunbackend.module.driver.dto.DriverStatusRequest;
import org.example.steelbikerunbackend.module.driver.dto.SwitchDriverRequest;
import org.example.steelbikerunbackend.module.driver.dto.SwitchRoleResponse;
import org.example.steelbikerunbackend.module.driver.service.DriverService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Driver", description = "Quản lý tài xế: chuyển đổi chế độ, bật/tắt trạng thái online, xem profile")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/driver")
@RequiredArgsConstructor
public class DriverController {

        private final DriverService driverService;

        // ─────────────────────────────────────────────────────────────────────────
        // 1. SWITCH USER → DRIVER MODE
        // ─────────────────────────────────────────────────────────────────────────

        /**
         * Chuyển user sang chế độ Driver. Chỉ CUSTOMER mới gọi được.
         *
         * <ul>
         * <li><b>Lần đầu tiên</b>: bắt buộc gửi thông tin xe → tạo profile → tự động
         * Online.</li>
         * <li><b>Các lần sau</b>: profile đã tồn tại, chỉ đảm bảo trạng thái
         * Online.</li>
         * </ul>
         */
        @Operation(summary = "Chuyển sang chế độ Driver (CUSTOMER only)", description = """
                        Chỉ tài khoản **CUSTOMER** được gọi endpoint này.

                        **Lần đầu tiên** (chưa có profile Driver):
                        - Bắt buộc gửi `vehiclePlate`, `vehicleModel`, `vehicleColor`, `licenseNumber`.
                        - Hệ thống tạo profile Driver, đặt `isOnline = true` và cập nhật `role = DRIVER` trong DB.
                        - Response trả về `isNewProfile = true`.

                        **Các lần sau** (đã có profile Driver):
                        - Body không cần thiết (bỏ qua).
                        - Hệ thống đảm bảo `isOnline = true` và cập nhật `role = DRIVER` trong DB.
                        - Response trả về `isNewProfile = false`.

                        Response bao gồm `accessToken` mới với `role = DRIVER`.
                        Client **lưu đè token cũ** bằng token này và chuyển sang Driver Home mà không cần re-login.
                        """)
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Chuyển sang Driver mode thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Thiếu thông tin xe (lần đầu) hoặc biển số/bằng lái trùng"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Tài khoản không phải CUSTOMER")
        })
        @PreAuthorize("hasRole('CUSTOMER')")
        @PostMapping("/switch")
        public ResponseEntity<ApiResponse<SwitchRoleResponse>> switchToDriver(
                        @AuthenticationPrincipal String userEmail,
                        @Valid @RequestBody(required = false) SwitchDriverRequest request) {

                SwitchRoleResponse response = driverService.switchToDriver(userEmail, request);

                String message = response.driverProfile().isNewProfile()
                                ? "Profile Driver đã được tạo. Trạng thái: Online"
                                : "Đã chuyển sang chế độ Driver. Trạng thái: Online";

                return ResponseEntity.ok(ApiResponse.success(message, response));
        }

        // ─────────────────────────────────────────────────────────────────────────
        // 2. SWITCH DRIVER → CUSTOMER MODE
        // ─────────────────────────────────────────────────────────────────────────

        /**
         * Chuyển tài xế về chế độ Customer. Chỉ DRIVER mới gọi được.
         * Tự động set isOnline = false trước khi chuyển về.
         */
        @Operation(summary = "Chuyển về chế độ Customer (DRIVER only)", description = """
                        Chỉ tài khoản **DRIVER** được gọi endpoint này.

                        - Tự động đặt `isOnline = false` (offline).
                        - Cập nhật `role = CUSTOMER` trong DB.
                        - Không cần body.

                        Response bao gồm `accessToken` mới với `role = CUSTOMER`.
                        Client **lưu đè token cũ** bằng token này và chuyển sang Customer Home mà không cần re-login.
                        """)
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Chuyển về Customer mode thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Profile Driver chưa tồn tại"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Tài khoản không phải DRIVER")
        })
        @PreAuthorize("hasRole('DRIVER')")
        @PostMapping("/switch-back")
        public ResponseEntity<ApiResponse<SwitchRoleResponse>> switchToCustomer(
                        @AuthenticationPrincipal String userEmail) {

                SwitchRoleResponse response = driverService.switchToCustomer(userEmail);
                return ResponseEntity
                                .ok(ApiResponse.success("Đã chuyển về chế độ Customer. Trạng thái: Offline", response));
        }

        // ─────────────────────────────────────────────────────────────────────────
        // 3. SET ONLINE / OFFLINE (trong Driver Mode)
        // ─────────────────────────────────────────────────────────────────────────

        /**
         * Bật / tắt trạng thái online trong khi đang ở Driver Mode.
         * Chỉ DRIVER mới gọi được. Không ảnh hưởng đến role.
         */
        @Operation(summary = "Cập nhật trạng thái Online/Offline (DRIVER only)", description = """
                        Chỉ tài khoản **DRIVER** được gọi endpoint này.

                        - Dùng để bật/tắt trạng thái nhận cuốc **trong khi vẫn ở Driver Mode**.
                        - Không liên quan đến việc chuyển đổi role.
                        - Body: `{ "isOnline": true }` hoặc `{ "isOnline": false }`.
                        """)
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cập nhật trạng thái thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Profile Driver chưa tồn tại hoặc body không hợp lệ"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Tài khoản không phải DRIVER")
        })
        @PreAuthorize("hasRole('DRIVER')")
        @PutMapping("/status")
        public ResponseEntity<ApiResponse<DriverProfileResponse>> setOnlineStatus(
                        @AuthenticationPrincipal String userEmail,
                        @Valid @RequestBody DriverStatusRequest request) {

                DriverProfileResponse response = driverService.setOnlineStatus(userEmail, request);

                String message = request.isOnline()
                                ? "Đã chuyển sang trạng thái: Online"
                                : "Đã chuyển sang trạng thái: Offline";

                return ResponseEntity.ok(ApiResponse.success(message, response));
        }

        // ─────────────────────────────────────────────────────────────────────────
        // 4. GET PROFILE
        // ─────────────────────────────────────────────────────────────────────────

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
