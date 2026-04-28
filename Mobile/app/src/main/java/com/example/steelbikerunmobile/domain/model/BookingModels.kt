package com.example.steelbikerunmobile.domain.model

data class SurgeZone(
    val h3Index: String,
    val center: LatLng,
    val surgeMultiplier: Double
)

data class PriceEstimate(
    val basePrice: Double,
    val surgeMultiplier: Double,
    val finalPrice: Double,
    val distanceKm: Double,
    val durationMinutes: Int
)

data class BookingDraft(
    val pickup: LatLng,
    val destination: LatLng,
    val destinationAddress: String
)
