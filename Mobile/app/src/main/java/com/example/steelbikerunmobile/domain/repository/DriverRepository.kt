package com.example.steelbikerunmobile.domain.repository

import com.example.steelbikerunmobile.domain.model.DriverProfile
import com.example.steelbikerunmobile.domain.model.LocationHeartbeat
import com.example.steelbikerunmobile.domain.model.NearbyDriver
import com.example.steelbikerunmobile.domain.model.VehicleInfo
import kotlinx.coroutines.flow.Flow

interface DriverRepository {
    suspend fun getProfile(): Result<DriverProfile>
    suspend fun switchOnline(vehicleInfo: VehicleInfo?): Result<DriverProfile>
    fun observeLocation(): Flow<LocationHeartbeat>
    suspend fun sendLocationHeartbeat(heartbeat: LocationHeartbeat)
    suspend fun getNearbyDrivers(latitude: Double, longitude: Double): Result<List<NearbyDriver>>
    fun stopRealtime()
}
