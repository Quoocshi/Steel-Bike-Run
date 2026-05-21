package org.example.steelbikerunbackend.module.review.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.steelbikerunbackend.common.response.ApiResponse;
import org.example.steelbikerunbackend.module.review.dto.ReviewResponse;
import org.example.steelbikerunbackend.module.review.dto.SubmitReviewRequest;
import org.example.steelbikerunbackend.module.review.service.ReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Review", description = "Quản lý đánh giá chuyến đi")
@RestController
@RequestMapping("/api/v1/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "Gửi đánh giá chuyến đi",
            description = "Customer gửi đánh giá (1-5 sao) kèm comment cho tài xế sau khi hoàn thành chuyến đi. "
                    + "Mỗi chuyến đi chỉ được đánh giá một lần.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Đánh giá được lưu thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Rating không hợp lệ hoặc chuyến đi chưa hoàn thành"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Đã đánh giá chuyến đi này rồi")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> submitReview(
            Authentication authentication,
            @Valid @RequestBody SubmitReviewRequest request) {
        ReviewResponse result = reviewService.submitReview(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("Đánh giá của bạn đã được gửi. Cảm ơn!", result));
    }

    @Operation(summary = "Lấy đánh giá theo trip ID",
            description = "Lấy thông tin đánh giá của một chuyến đi cụ thể.")
    @GetMapping("/trip/{tripId}")
    public ResponseEntity<ApiResponse<ReviewResponse>> getReviewByTrip(@PathVariable String tripId) {
        ReviewResponse result = reviewService.getReviewByTripId(tripId);
        if (result == null) {
            return ResponseEntity.ok(ApiResponse.success("Chưa có đánh giá cho chuyến đi này", null));
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Lấy danh sách đánh giá của tài xế",
            description = "Trả về tất cả đánh giá dành cho một tài xế, mới nhất trước.")
    @GetMapping("/driver/{driverId}")
    public ResponseEntity<ApiResponse<java.util.List<ReviewResponse>>> getDriverReviews(
            @PathVariable UUID driverId) {
        java.util.List<ReviewResponse> result = reviewService.getReviewsForDriver(driverId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
