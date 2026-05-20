package com.example.steelbikerunmobile.domain.repository

import com.example.steelbikerunmobile.domain.model.DriverProfile
import com.example.steelbikerunmobile.domain.model.LocationHeartbeat
import com.example.steelbikerunmobile.domain.model.NearbyDriver
import com.example.steelbikerunmobile.domain.model.VehicleInfo
import kotlinx.coroutines.flow.Flow

interface DriverRepository {

    suspend fun getProfile(): Result<DriverProfile>

    /**
     * CUSTOMER → DRIVER transition. On success the new JWT (role=DRIVER) is persisted internally
     * so the next API call automatically uses driver-scoped permissions.
     *
     * @param vehicleInfo required only on the very first switch (when no driver profile exists);
     *                    pass `null` afterwards.
     */
    suspend fun switchToDriver(vehicleInfo: VehicleInfo?): Result<DriverProfile>

    /**
     * DRIVER → CUSTOMER transition. The driver is automatically set offline by the backend.
     * On success the new JWT (role=CUSTOMER) is persisted internally.
     */
    suspend fun switchToCustomer(): Result<DriverProfile>

    /**
     * Toggle the driver's online status while staying in Driver mode. Does not rotate the JWT.
     */
    suspend fun setOnlineStatus(isOnline: Boolean): Result<DriverProfile>

    fun observeLocation(): Flow<LocationHeartbeat>
    suspend fun sendLocationHeartbeat(heartbeat: LocationHeartbeat)

    /**
     * Connect the WebSocket before starting the location Flow.
     * MUST be called before [observeLocation] starts sending heartbeats,
     * otherwise the first heartbeat(s) will be silently dropped.
     * This solves the "first online switch" race condition where the driver
     * is online (DB) but Redis has no location yet.
     */
    suspend fun connectWebSocket()

    /**
     * Stream H3 cell index hiện tại của tài xế, được cập nhật sau mỗi heartbeat thành công.
     * Giá trị null khi chưa có heartbeat nào thành công hoặc tài xế offline.
     * Backend tính h3Index (resolution=9) từ lat/lng — mobile chỉ hiển thị kết quả.
     */
    fun observeCurrentH3Index(): Flow<String?>

    suspend fun getNearbyDrivers(latitude: Double, longitude: Double): Result<List<NearbyDriver>>
    fun stopRealtime()
}
