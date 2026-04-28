package com.example.steelbikerunmobile.domain.model

data class VehicleInfo(
    val vehiclePlate: String,
    val vehicleModel: String,
    val vehicleColor: String,
    val licenseNumber: String
)

data class DriverProfile(
    val driverId: String,
    val userId: String,
    val fullName: String,
    val email: String,
    val phone: String?,
    val vehiclePlate: String?,
    val vehicleModel: String?,
    val vehicleColor: String?,
    val licenseNumber: String?,
    val isOnline: Boolean,
    val rating: Float,
    val totalTrips: Int,
    val faceScanPassed: Boolean,
    val isNewProfile: Boolean
)

data class NearbyDriver(
    val driverId: String,
    val fullName: String,
    val location: LatLng,
    val rating: Float,
    val vehiclePlate: String?,
    val distanceKm: Double?
)

data class LocationHeartbeat(
    val location: LatLng,
    val heading: Float? = null,
    val speedMetersPerSecond: Float? = null
)
