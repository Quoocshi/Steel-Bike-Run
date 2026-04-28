package com.example.steelbikerunmobile.data.remote.dto

data class PriceEstimateRequestDto(
    val pickupLat: Double,
    val pickupLng: Double,
    val destLat: Double,
    val destLng: Double
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
