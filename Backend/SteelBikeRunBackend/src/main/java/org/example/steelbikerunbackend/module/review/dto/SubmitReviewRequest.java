package org.example.steelbikerunbackend.module.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request payload for submitting a trip review")
public record SubmitReviewRequest(
        @Schema(description = "Trip ID to review", example = "123e4567-e89b-12d3-a456-426614174000")
        @NotNull(message = "Trip ID is required")
        String tripId,

        @Schema(description = "Rating from 1 to 5 stars", example = "5")
        @NotNull(message = "Rating is required")
        @Min(value = 1, message = "Rating must be at least 1")
        @Max(value = 5, message = "Rating must be at most 5")
        Integer rating,

        @Schema(description = "Optional comment about the trip", example = "Tài xế rất thân thiện, xe sạch sẽ")
        String comment
) {}
