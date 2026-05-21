package com.example.steelbikerunmobile.data.remote.dto

data class PriceEstimateRequestDto(
    val pickupLat: Double,
    val pickupLng: Double,
    val destLat: Double,
    val destLng: Double,
    val destAddress: String
)

data class PriceEstimateDto(
    val basePrice: Double?,
    val surgeMultiplier: Double?,
    val finalPrice: Double?,
    val distanceKm: Double?,
    val durationMinutes: Int?
)

data class CreateTripRequestDto(
    val pickupLat: Double,
    val pickupLng: Double,
    val destLat: Double,
    val destLng: Double,
    val destAddress: String
)

/**
 * Response cuốc xe — mirrors backend TripResponse record.
 */
data class TripResponseDto(
    val id: String?,
    val customerId: String?,
    val driverId: String?,
    val driverName: String?,
    val pickupLat: Double,
    val pickupLng: Double,
    val pickupH3Index: String?,
    val destLat: Double,
    val destLng: Double,
    val destAddress: String?,
    val status: String?,
    val basePrice: Double?,
    val surgeMultiplier: Double?,
    val finalPrice: Double?,
    val distanceKm: Float?,
    val durationMinutes: Int?,
    val requestedAt: String?,
    val acceptedAt: String?,
    val startedAt: String?,
    val completedAt: String?,
)

/**
 * Request payload for submitting a trip review.
 */
data class SubmitReviewRequestDto(
    val tripId: String,
    val rating: Int,
    val comment: String?
)

/**
 * Response payload for a trip review.
 */
data class ReviewResponseDto(
    val id: String?,
    val tripId: String?,
    val reviewerId: String?,
    val revieweeId: String?,
    val rating: Int?,
    val comment: String?,
    val createdAt: String?
)
