package org.example.steelbikerunbackend.module.trip.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.steelbikerunbackend.common.response.ApiResponse;
import org.example.steelbikerunbackend.module.trip.dto.CreateTripRequest;
import org.example.steelbikerunbackend.module.trip.dto.PriceEstimateRequest;
import org.example.steelbikerunbackend.module.trip.dto.PriceEstimateResponse;
import org.example.steelbikerunbackend.module.trip.dto.SurgeZoneDto;
import org.example.steelbikerunbackend.module.trip.dto.TripResponse;
import org.example.steelbikerunbackend.module.trip.service.PricingService;
import org.example.steelbikerunbackend.module.trip.service.TripService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * TripController — REST endpoints cho toàn bộ vòng đời cuốc xe.
 *
 * <h3>API Contract:</h3>
 * <pre>
 * POST /api/v1/trip/estimate      (Public)    Ước tính giá
 * POST /api/v1/trip               (CUSTOMER)  Đặt xe
 * PUT  /api/v1/trip/{id}/accept   (DRIVER)    Nhận cuốc
 * PUT  /api/v1/trip/{id}/arrive   (DRIVER)    Đã đến điểm đón
 * PUT  /api/v1/trip/{id}/start    (DRIVER)    Bắt đầu chuyến (sau khi khách lên xe)
 * PUT  /api/v1/trip/{id}/complete (DRIVER)    Hoàn thành
 * PUT  /api/v1/trip/{id}/cancel   (AUTH)      Hủy cuốc
 * GET  /api/v1/trip/{id}          (AUTH)      Xem chi tiết
 * GET  /api/v1/trip/history       (AUTH)      Xem lịch sử
 * </pre>
 */
@Tag(name = "Trip", description = "Quản lý cuốc xe: ước tính giá, đặt xe, cập nhật trạng thái")
@RestController
@RequestMapping("/api/v1/trip")
@RequiredArgsConstructor
public class TripController {

    private final PricingService pricingService;
    private final TripService tripService;

    // -------------------------------------------------------------------------
    // ESTIMATE (Public)
    // -------------------------------------------------------------------------

    @Operation(summary = "Ước tính giá chuyến đi",
            description = "Tính giá dựa trên khoảng cách Haversine x hệ số đường + surge pricing theo vùng H3.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Ước tính thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Tọa độ không hợp lệ hoặc thiếu trường bắt buộc")
    })
    @PostMapping("/estimate")
    public ResponseEntity<ApiResponse<PriceEstimateResponse>> estimate(
            @Valid @RequestBody PriceEstimateRequest request) {
        PriceEstimateResponse result = pricingService.estimate(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // -------------------------------------------------------------------------
    // CREATE TRIP (Customer - Authenticated)
    // -------------------------------------------------------------------------

    @Operation(summary = "Đặt xe",
            description = "Customer xác nhận đặt xe. Giá được snapshot tại thời điểm đặt. "
                    + "Hệ thống tự động broadcast đến top 3 tài xế gần nhất qua WebSocket.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Đặt xe thành công, đang tìm tài xế"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Chưa đăng nhập")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<TripResponse>> createTrip(
            Authentication authentication,
            @Valid @RequestBody CreateTripRequest request) {
        TripResponse result = tripService.createTrip(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("Đặt xe thành công, đang tìm tài xế", result));
    }

    // -------------------------------------------------------------------------
    // ACCEPT (Driver - Authenticated)
    // -------------------------------------------------------------------------

    @Operation(summary = "Nhận cuốc xe",
            description = "Driver nhận cuốc. Nếu 2 driver bấm cùng lúc, người bấm trước sẽ thắng.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Nhận cuốc thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Cuốc xe đã được nhận bởi tài xế khác")
    })
    @PutMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<TripResponse>> acceptTrip(
            Authentication authentication,
            @PathVariable UUID id) {
        TripResponse result = tripService.acceptTrip(authentication.getName(), id);
        return ResponseEntity.ok(ApiResponse.success("Nhận cuốc thành công", result));
    }

    // -------------------------------------------------------------------------
    // ARRIVE (Driver - Authenticated)
    // -------------------------------------------------------------------------

    @Operation(summary = "Xác nhận đã đến điểm đón",
            description = "Driver bấm khi đã tới nơi đón khách. Chuyển trip từ ACCEPTED -> ARRIVED, thông báo Customer.")
    @PutMapping("/{id}/arrive")
    public ResponseEntity<ApiResponse<TripResponse>> arriveAtPickup(
            Authentication authentication,
            @PathVariable UUID id) {
        TripResponse result = tripService.arriveAtPickup(authentication.getName(), id);
        return ResponseEntity.ok(ApiResponse.success("Đã đến điểm đón", result));
    }

    // -------------------------------------------------------------------------
    // START (Driver - Authenticated)
    // -------------------------------------------------------------------------

    @Operation(summary = "Bắt đầu chuyến đi",
            description = "Driver xác nhận đã đón khách và bắt đầu di chuyển đến điểm đến.")
    @PutMapping("/{id}/start")
    public ResponseEntity<ApiResponse<TripResponse>> startTrip(
            Authentication authentication,
            @PathVariable UUID id) {
        TripResponse result = tripService.startTrip(authentication.getName(), id);
        return ResponseEntity.ok(ApiResponse.success("Bắt đầu chuyến đi", result));
    }

    // -------------------------------------------------------------------------
    // COMPLETE (Driver - Authenticated)
    // -------------------------------------------------------------------------

    @Operation(summary = "Hoàn thành chuyến đi",
            description = "Driver xác nhận đã đến điểm đến. Giá cuối cùng = giá snapshot lúc đặt.")
    @PutMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<TripResponse>> completeTrip(
            Authentication authentication,
            @PathVariable UUID id) {
        TripResponse result = tripService.completeTrip(authentication.getName(), id);
        return ResponseEntity.ok(ApiResponse.success("Hoàn thành chuyến đi", result));
    }

    // -------------------------------------------------------------------------
    // CANCEL (Customer or Driver - Authenticated)
    // -------------------------------------------------------------------------

    @Operation(summary = "Hủy cuốc xe",
            description = "Customer hoặc Driver hủy cuốc. Không thể hủy cuốc đã hoàn thành.")
    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<TripResponse>> cancelTrip(
            Authentication authentication,
            @PathVariable UUID id) {
        TripResponse result = tripService.cancelTrip(authentication.getName(), id);
        return ResponseEntity.ok(ApiResponse.success("Đã hủy cuốc xe", result));
    }

    // -------------------------------------------------------------------------
    // GET TRIP DETAIL (Authenticated)
    // -------------------------------------------------------------------------

    @Operation(summary = "Xem chi tiết cuốc xe")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TripResponse>> getTrip(@PathVariable UUID id) {
        TripResponse result = tripService.getTrip(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // -------------------------------------------------------------------------
    // SURGE ZONES (Public — mobile map layer)
    // -------------------------------------------------------------------------

    @Operation(summary = "Lấy danh sách vùng surge pricing",
            description = "Trả về tất cả ô H3 đang có surge multiplier > 1.0 để mobile vẽ lên bản đồ. Không yêu cầu xác thực.")
    @GetMapping("/surge-zones")
    public ResponseEntity<ApiResponse<List<SurgeZoneDto>>> getSurgeZones() {
        List<SurgeZoneDto> zones = pricingService.getAllSurgeZones();
        return ResponseEntity.ok(ApiResponse.success(zones));
    }

    // -------------------------------------------------------------------------
    // TRIP HISTORY (Authenticated)
    // -------------------------------------------------------------------------

    @Operation(summary = "Xem lịch sử cuốc xe",
            description = "Trả về danh sách cuốc xe của user hiện tại (customer hoặc driver), mới nhất trước.")
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<TripResponse>>> getHistory(
            Authentication authentication,
            @RequestParam(defaultValue = "customer") String role) {
        List<TripResponse> result;
        if ("driver".equalsIgnoreCase(role)) {
            result = tripService.getDriverHistory(authentication.getName());
        } else {
            result = tripService.getCustomerHistory(authentication.getName());
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
