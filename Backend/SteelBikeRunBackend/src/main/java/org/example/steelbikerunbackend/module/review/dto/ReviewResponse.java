package org.example.steelbikerunbackend.module.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Response payload for a trip review")
public record ReviewResponse(
        @Schema(description = "Review ID", example = "123e4567-e89b-12d3-a456-426614174000")
        String id,

        @Schema(description = "Trip ID", example = "123e4567-e89b-12d3-a456-426614174000")
        String tripId,

        @Schema(description = "Reviewer (customer) ID", example = "123e4567-e89b-12d3-a456-426614174000")
        String reviewerId,

        @Schema(description = "Reviewee (driver) ID", example = "123e4567-e89b-12d3-a456-426614174000")
        String revieweeId,

        @Schema(description = "Rating 1-5 stars", example = "5")
        int rating,

        @Schema(description = "Optional comment", example = "Tài xế rất thân thiện")
        String comment,

        @Schema(description = "Timestamp when review was created")
        LocalDateTime createdAt
) {}
