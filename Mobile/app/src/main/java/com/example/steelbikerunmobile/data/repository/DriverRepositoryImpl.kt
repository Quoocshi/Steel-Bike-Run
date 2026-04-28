package com.example.steelbikerunmobile.data.repository

import com.example.steelbikerunmobile.data.location.LocationStreamProvider
import com.example.steelbikerunmobile.data.remote.api.DriverApiService
import com.example.steelbikerunmobile.data.remote.dto.DriverProfileDto
import com.example.steelbikerunmobile.data.remote.dto.LocationHeartbeatDto
import com.example.steelbikerunmobile.data.remote.dto.NearbyDriverDto
import com.example.steelbikerunmobile.data.remote.dto.SwitchDriverRequestDto
import com.example.steelbikerunmobile.data.remote.websocket.WebSocketManager
import com.example.steelbikerunmobile.domain.model.DriverProfile
import com.example.steelbikerunmobile.domain.model.LatLng
import com.example.steelbikerunmobile.domain.model.LocationHeartbeat
import com.example.steelbikerunmobile.domain.model.NearbyDriver
import com.example.steelbikerunmobile.domain.model.VehicleInfo
import com.example.steelbikerunmobile.domain.repository.DriverRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class DriverRepositoryImpl @Inject constructor(
    private val driverApiService: DriverApiService,
    private val locationStreamProvider: LocationStreamProvider,
    private val webSocketManager: WebSocketManager
) : DriverRepository {

    override suspend fun getProfile(): Result<DriverProfile> = runCatching {
        val envelope = driverApiService.getProfile()
        envelope.data?.toDomain() ?: error(envelope.message.ifBlank { "Chưa có profile tài xế" })
    }

    override suspend fun switchOnline(vehicleInfo: VehicleInfo?): Result<DriverProfile> = runCatching {
        val envelope = driverApiService.switchDriver(vehicleInfo?.toDto())
        envelope.data?.toDomain() ?: error(envelope.message.ifBlank { "Không thể đổi trạng thái tài xế" })
    }

    override fun observeLocation(): Flow<LocationHeartbeat> = locationStreamProvider.observeLocation()

    override suspend fun sendLocationHeartbeat(heartbeat: LocationHeartbeat) {
        webSocketManager.sendDriverLocation(
            latitude = heartbeat.location.latitude,
            longitude = heartbeat.location.longitude
        )
        runCatching {
            driverApiService.postLocation(
                LocationHeartbeatDto(
                    lat = heartbeat.location.latitude,
                    lng = heartbeat.location.longitude,
                    h3Index = DemoMapData.pseudoH3Index(heartbeat.location),
                    heading = heartbeat.heading,
                    speed = heartbeat.speedMetersPerSecond
                )
            )
        }
    }

    override suspend fun getNearbyDrivers(latitude: Double, longitude: Double): Result<List<NearbyDriver>> {
        return runCatching {
            val envelope = driverApiService.getNearbyDrivers(latitude, longitude)
            val drivers = envelope.data?.map { it.toDomain() }.orEmpty()
            drivers.ifEmpty { DemoMapData.drivers }
        }.recover { DemoMapData.drivers }
    }

    override fun stopRealtime() {
        webSocketManager.disconnect()
    }

    private fun VehicleInfo.toDto(): SwitchDriverRequestDto {
        return SwitchDriverRequestDto(
            vehiclePlate = vehiclePlate,
            vehicleModel = vehicleModel,
            vehicleColor = vehicleColor,
            licenseNumber = licenseNumber
        )
    }

    private fun DriverProfileDto.toDomain(): DriverProfile {
        return DriverProfile(
            driverId = driverId,
            userId = userId,
            fullName = fullName,
            email = email,
            phone = phone,
            vehiclePlate = vehiclePlate,
            vehicleModel = vehicleModel,
            vehicleColor = vehicleColor,
            licenseNumber = licenseNumber,
            isOnline = isOnline == true,
            rating = rating ?: 5f,
            totalTrips = totalTrips ?: 0,
            faceScanPassed = faceScanPassed == true,
            isNewProfile = isNewProfile == true
        )
    }

    private fun NearbyDriverDto.toDomain(): NearbyDriver {
        return NearbyDriver(
            driverId = driverId,
            fullName = fullName.orEmpty(),
            location = LatLng(lat, lng),
            rating = rating ?: 5f,
            vehiclePlate = vehiclePlate,
            distanceKm = distanceKm
        )
    }
}
