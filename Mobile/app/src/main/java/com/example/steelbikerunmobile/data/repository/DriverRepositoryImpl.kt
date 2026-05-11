package com.example.steelbikerunmobile.data.repository

import com.example.steelbikerunmobile.data.location.LocationStreamProvider
import com.example.steelbikerunmobile.data.remote.NetworkErrorMapper
import com.example.steelbikerunmobile.data.remote.api.DriverApiService
import com.example.steelbikerunmobile.data.remote.dto.DriverProfileDto
import com.example.steelbikerunmobile.data.remote.dto.DriverStatusRequestDto
import com.example.steelbikerunmobile.data.remote.dto.LocationUpdateRequestDto
import com.example.steelbikerunmobile.data.remote.dto.NearbyDriverDto
import com.example.steelbikerunmobile.data.remote.dto.SwitchDriverRequestDto
import com.example.steelbikerunmobile.data.remote.dto.SwitchRoleResponseDto
import com.example.steelbikerunmobile.data.remote.websocket.StompWebSocketManager
import com.example.steelbikerunmobile.domain.model.DriverProfile
import com.example.steelbikerunmobile.domain.model.LatLng
import com.example.steelbikerunmobile.domain.model.LocationHeartbeat
import com.example.steelbikerunmobile.domain.model.NearbyDriver
import com.example.steelbikerunmobile.domain.model.UserRole
import com.example.steelbikerunmobile.domain.model.VehicleInfo
import com.example.steelbikerunmobile.domain.repository.AuthRepository
import com.example.steelbikerunmobile.domain.repository.DriverRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class DriverRepositoryImpl @Inject constructor(
    private val driverApiService: DriverApiService,
    private val authRepository: AuthRepository,
    private val locationStreamProvider: LocationStreamProvider,
    private val stompWebSocketManager: StompWebSocketManager
) : DriverRepository {

    // H3 cell index tính bởi server sau mỗi heartbeat thành công.
    // Null khi chưa có heartbeat hoặc server chưa trả về.
    private val _currentH3Index = MutableStateFlow<String?>(null)

    override suspend fun getProfile(): Result<DriverProfile> = NetworkErrorMapper.safeCall {
        val envelope = driverApiService.getProfile()
        envelope.data?.toDomain()
            ?: error(envelope.message?.takeIf { it.isNotBlank() } ?: "Chưa có profile tài xế")
    }

    override suspend fun switchToDriver(vehicleInfo: VehicleInfo?): Result<DriverProfile> =
        NetworkErrorMapper.safeCall {
            // Two different Retrofit calls for the same endpoint:
            //
            // • vehicleInfo == null  →  switchToDriverExisting()  — sends NO body at all.
            //   Spring's @RequestBody(required=false) receives null, skips @Valid, and
            //   the service checks profile existence directly.
            //     - Profile exists  → 200 + new DRIVER JWT  ✓
            //     - No profile yet  → 400 "Cần cung cấp thông tin xe"  → caller shows form ✓
            //
            // • vehicleInfo != null  →  switchToDriverNew(body)  — sends full vehicle info.
            //   Spring validates @NotBlank fields, creates the profile, returns 200.
            //
            // We must NOT send an empty body {} for the "existing profile" case because
            // @Valid would fire on the null-field record and return 400 before the
            // business logic is ever reached — showing the form again every time.
            val envelope = if (vehicleInfo != null) {
                driverApiService.switchToDriverNew(vehicleInfo.toDto())
            } else {
                driverApiService.switchToDriverExisting()
            }
            val payload = envelope.data
                ?: error(envelope.message?.takeIf { it.isNotBlank() } ?: "Không thể chuyển sang chế độ Tài xế")
            // Backend re-issues a fresh JWT carrying the new role — overwrite the cached one
            // BEFORE returning, otherwise the next request still goes out as CUSTOMER and 403s.
            authRepository.updateAccessToken(payload.accessToken, UserRole.DRIVER)
            payload.driverProfile.toDomain()
        }

    override suspend fun switchToCustomer(): Result<DriverProfile> = NetworkErrorMapper.safeCall {
        val envelope = driverApiService.switchToCustomer()
        val payload = envelope.data
            ?: error(envelope.message?.takeIf { it.isNotBlank() } ?: "Không thể chuyển về chế độ Khách hàng")
        authRepository.updateAccessToken(payload.accessToken, UserRole.CUSTOMER)
        payload.driverProfile.toDomain()
    }

    override suspend fun setOnlineStatus(isOnline: Boolean): Result<DriverProfile> =
        NetworkErrorMapper.safeCall {
            val envelope = driverApiService.setDriverStatus(DriverStatusRequestDto(isOnline))
            envelope.data?.toDomain()
                ?: error(envelope.message?.takeIf { it.isNotBlank() } ?: "Không thể cập nhật trạng thái")
        }

    override fun observeLocation(): Flow<LocationHeartbeat> = locationStreamProvider.observeLocation()

    override suspend fun sendLocationHeartbeat(heartbeat: LocationHeartbeat) {
        // Ghi vị trí lên Backend qua REST (POST /api/v1/driver/location).
        // Backend ghi vào Redis (write-behind Postgres mỗi 30s).
        //
        // WebSocket KHÔNG được dùng ở đây vì:
        //   1. Backend chưa có STOMP endpoint cho driver location (placeholder only).
        //   2. WebSocketManager.connect() dùng runBlocking trên coroutine dispatcher
        //      → tiềm ẩn deadlock trên Main thread.
        // WS sẽ được kích hoạt khi backend triển khai WebSocket Matching Engine.
        runCatching {
            val response = driverApiService.postLocation(
                LocationUpdateRequestDto(
                    latitude = heartbeat.location.latitude,
                    longitude = heartbeat.location.longitude,
                    heading = heartbeat.heading,
                    speed = heartbeat.speedMetersPerSecond
                )
            )
            // Server tính h3Index từ lat/lng (resolution=9, ~174m hexagon).
            // Cập nhật flow để ViewModel có thể hiển thị cell hiện tại trên bản đồ.
            response.data?.h3Index?.let { _currentH3Index.value = it }
        }
    }

    override fun observeCurrentH3Index(): Flow<String?> = _currentH3Index.asStateFlow()

    override suspend fun getNearbyDrivers(latitude: Double, longitude: Double): Result<List<NearbyDriver>> {
        return runCatching {
            val envelope = driverApiService.getNearbyDrivers(latitude, longitude)
            val drivers = envelope.data?.map { it.toDomain() }.orEmpty()
            drivers.ifEmpty { DemoMapData.drivers }
        }.recover { DemoMapData.drivers }
    }

    override fun stopRealtime() {
        stompWebSocketManager.disconnect()
        _currentH3Index.value = null
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
