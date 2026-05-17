package com.example.steelbikerunmobile.data.remote.dto

data class SurgeZoneResponseDto(
    val h3Index: String,
    val centerLat: Double,
    val centerLng: Double,
    val surgeMultiplier: Float,
    val activeDrivers: Int,
)

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
