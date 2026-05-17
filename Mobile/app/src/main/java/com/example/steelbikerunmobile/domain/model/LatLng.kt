package com.example.steelbikerunmobile.domain.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(
    val latitude: Double,
    val longitude: Double
)

/**
 * Tính khoảng cách (mét) giữa hai tọa độ theo công thức Haversine.
 */
fun LatLng.distanceTo(other: LatLng): Double {
    val r = 6_371_000.0 // bán kính Trái Đất (mét)
    val lat1 = Math.toRadians(latitude)
    val lat2 = Math.toRadians(other.latitude)
    val dLat = Math.toRadians(other.latitude - latitude)
    val dLng = Math.toRadians(other.longitude - longitude)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}
