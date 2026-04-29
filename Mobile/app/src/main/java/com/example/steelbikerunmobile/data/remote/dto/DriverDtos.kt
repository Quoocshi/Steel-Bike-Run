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

data class LocationHeartbeatDto(
    val lat: Double,
    val lng: Double,
    val h3Index: String?,
    val heading: Float?,
    val speed: Float?
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
