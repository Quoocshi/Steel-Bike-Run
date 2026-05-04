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
import org.example.steelbikerunbackend.module.driver.dto.LocationUpdateRequest;
import org.example.steelbikerunbackend.module.driver.dto.LocationUpdateResponse;
import org.example.steelbikerunbackend.module.driver.dto.SwitchDriverRequest;
import org.example.steelbikerunbackend.module.driver.dto.SwitchRoleResponse;
import org.example.steelbikerunbackend.module.driver.service.DriverLocationService;
import org.example.steelbikerunbackend.module.driver.service.DriverService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Driver", description = "Quản lý tài xế: chuyển đổi chế độ, bật/tắt trạng thái online, cập nhật vị trí")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/driver")
@RequiredArgsConstructor
public class DriverController {

        private final DriverService driverService;
        private final DriverLocationService driverLocationService;

        // -------------------------------------------------------------------------
        // 1. SWITCH USER -> DRIVER MODE
        // -------------------------------------------------------------------------

        /**
         * Chuyển user sang chế độ Driver. Chỉ CUSTOMER mới gọi được.
         *
         * <ul>
         * <li><b>Lần đầu tiên</b>: bắt buộc gửi thông tin xe -> tạo profile -> tự động
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

        // -------------------------------------------------------------------------
        // 2. SWITCH DRIVER -> CUSTOMER MODE
        // -------------------------------------------------------------------------

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

        // -------------------------------------------------------------------------
        // 3. SET ONLINE / OFFLINE (trong Driver Mode)
        // -------------------------------------------------------------------------

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

        // -------------------------------------------------------------------------
        // 4. GET PROFILE
        // -------------------------------------------------------------------------

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

        // -------------------------------------------------------------------------
        // 5. LOCATION UPDATE (Driver Heartbeat)
        // -------------------------------------------------------------------------

        /**
         * Driver gửi vị trí GPS lên server (heartbeat mỗi 3 giây).
         * Chỉ DRIVER đang ONLINE mới gọi được.
         * Vị trí được ghi vào Redis (primary store), KHÔNG ghi Postgres ngay.
         */
        @Operation(summary = "Cập nhật vị trí tài xế (heartbeat)", description = """
                        Chỉ tài khoản **DRIVER** đang online được gọi endpoint này.

                        - App tài xế gọi mỗi 3 giây để cập nhật vị trí GPS.
                        - Vị trí được ghi vào **Redis** (TTL 60s), không ghi Postgres trực tiếp.
                        - Postgres sẽ được sync sau mỗi 30 giây bởi Sync Job.
                        - Response trả về `h3Index` — mobile dùng để vẽ H3 hexagon overlay.
                        """)
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cập nhật vị trí thành công"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Tài xế offline hoặc chưa có profile"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Tài khoản không phải DRIVER")
        })
        @PreAuthorize("hasRole('DRIVER')")
        @PostMapping("/location")
        public ResponseEntity<ApiResponse<LocationUpdateResponse>> updateLocation(
                        @AuthenticationPrincipal String userEmail,
                        @Valid @RequestBody LocationUpdateRequest request) {

                LocationUpdateResponse response = driverLocationService.updateLocation(userEmail, request);
                return ResponseEntity.ok(ApiResponse.success("Vị trí đã được cập nhật", response));
        }
}
