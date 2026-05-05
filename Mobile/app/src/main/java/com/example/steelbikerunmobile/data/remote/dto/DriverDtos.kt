package com.example.steelbikerunmobile.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SwitchDriverRequestDto(
    val vehiclePlate: String?,
    val vehicleModel: String?,
    val vehicleColor: String?,
    val licenseNumber: String?
)

data class DriverStatusRequestDto(
    @SerializedName("isOnline")
    val isOnline: Boolean
)

data class SwitchRoleResponseDto(
    val accessToken: String,
    val driverProfile: DriverProfileDto
)

data class DriverProfileDto(
    val driverId: String,
    val userId: String,
    val fullName: String,
    val email: String,
    val phone: String?,
    val avatarUrl: String?,
    val vehiclePlate: String?,
    val vehicleModel: String?,
    val vehicleColor: String?,
    val licenseNumber: String?,
    @SerializedName(value = "isOnline", alternate = ["online"])
    val isOnline: Boolean?,
    val rating: Float?,
    val totalTrips: Int?,
    val faceScanPassed: Boolean?,
    val lastFaceScanAt: String?,
    val isNewProfile: Boolean?
)

/**
 * Gửi lên backend mỗi 3 giây từ tài xế đang online.
 * Field names phải khớp chính xác với LocationUpdateRequest.java của backend:
 *   latitude / longitude (không phải lat/lng)
 * h3Index KHÔNG gửi lên — backend tự tính từ lat/lng bằng H3Core.
 */
data class LocationUpdateRequestDto(
    val latitude: Double,
    val longitude: Double,
    val heading: Float?,
    val speed: Float?
)

/**
 * Response từ POST /api/v1/driver/location.
 * Server trả về h3Index đã tính (resolution=9, ~174m hexagon)
 * để mobile cập nhật overlay bản đồ mà không cần tính lại client-side.
 */
data class LocationUpdateResponseDto(
    val driverId: String,
    val latitude: Double,
    val longitude: Double,
    val h3Index: String,
    val updatedAt: String
)

data class NearbyDriverDto(
    val driverId: String,
    val fullName: String?,
    val lat: Double,
    val lng: Double,
    val rating: Float?,
    val vehiclePlate: String?,
    val distanceKm: Double?
)
