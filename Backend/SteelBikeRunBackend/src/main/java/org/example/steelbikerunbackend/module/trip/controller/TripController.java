package org.example.steelbikerunbackend.module.trip.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.steelbikerunbackend.common.response.ApiResponse;
import org.example.steelbikerunbackend.module.trip.dto.PriceEstimateRequest;
import org.example.steelbikerunbackend.module.trip.dto.PriceEstimateResponse;
import org.example.steelbikerunbackend.module.trip.service.PricingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * TripController — endpoint cho tính năng Trip.
 * Hiện tại chỉ expose /estimate (ước tính giá). Các endpoint tạo/quản lý trip
 * sẽ được thêm ở Tuần 3-4.
 */
@Tag(name = "Trip", description = "Quản lý cuốc xe: ước tính giá, đặt xe, cập nhật trạng thái")
@RestController
@RequestMapping("/api/v1/trip")
@RequiredArgsConstructor
public class TripController {

    private final PricingService pricingService;

    /**
     * POST /api/v1/trip/estimate
     *
     * <p>Ước tính giá trước khi customer xác nhận đặt xe.
     * Không yêu cầu đăng nhập vì customer cần xem giá ngay cả khi chưa có tài khoản.
     *
     * <p><b>Lưu ý:</b> Giá trả về chỉ là ước tính. Giá thực tế khi đặt xe có thể thay đổi
     * nếu surge pricing thay đổi giữa lúc xem và lúc xác nhận.
     */
    @Operation(
            summary = "Ước tính giá chuyến đi",
            description = "Tính giá dựa trên khoảng cách Haversine x hệ số đường + surge pricing theo vùng H3. "
                    + "Trả về breakdown đầy đủ: base price, surge multiplier, final price."
    )
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
}
