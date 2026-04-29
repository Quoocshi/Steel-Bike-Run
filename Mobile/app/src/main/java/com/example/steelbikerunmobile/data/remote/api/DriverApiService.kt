package com.example.steelbikerunmobile.data.remote.api

import com.example.steelbikerunmobile.data.remote.dto.ApiEnvelope
import com.example.steelbikerunmobile.data.remote.dto.DriverProfileDto
import com.example.steelbikerunmobile.data.remote.dto.DriverStatusRequestDto
import com.example.steelbikerunmobile.data.remote.dto.LocationHeartbeatDto
import com.example.steelbikerunmobile.data.remote.dto.NearbyDriverDto
import com.example.steelbikerunmobile.data.remote.dto.SwitchDriverRequestDto
import com.example.steelbikerunmobile.data.remote.dto.SwitchRoleResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

interface DriverApiService {

    @GET("api/v1/driver/profile")
    suspend fun getProfile(): ApiEnvelope<DriverProfileDto>

    /**
     * CUSTOMER → DRIVER (subsequent switches — driver profile already exists).
     *
     * Sends NO body. Backend's @RequestBody(required=false) receives null → skips @Valid
     * validation → goes straight to profile-existence check → finds existing profile →
     * returns 200 + new JWT with role=DRIVER.
     *
     * ⚠️ Do NOT send an empty body {} here. Spring would create a SwitchDriverRequest
     * object with all-null fields, then @Valid would fire and return 400 before the
     * business logic even runs — making it look like the profile doesn't exist.
     */
    @POST("api/v1/driver/switch")
    suspend fun switchToDriverExisting(): ApiEnvelope<SwitchRoleResponseDto>

    /**
     * CUSTOMER → DRIVER (first-time registration — no driver profile yet).
     *
     * Sends the full vehicle info body. Backend validates fields, creates the driver
     * profile, and returns 200 + new JWT with role=DRIVER.
     */
    @POST("api/v1/driver/switch")
    suspend fun switchToDriverNew(
        @Body request: SwitchDriverRequestDto
    ): ApiEnvelope<SwitchRoleResponseDto>

    /**
     * DRIVER → CUSTOMER. Backend automatically sets isOnline=false and returns a fresh JWT
     * with role=CUSTOMER plus the (now offline) driver profile.
     */
    @POST("api/v1/driver/switch-back")
    suspend fun switchToCustomer(): ApiEnvelope<SwitchRoleResponseDto>

    /**
     * Toggle online/offline within Driver mode. Does NOT change the role or rotate the JWT.
     */
    @PUT("api/v1/driver/status")
    suspend fun setDriverStatus(
        @Body request: DriverStatusRequestDto
    ): ApiEnvelope<DriverProfileDto>

    @POST("api/v1/driver/location")
    suspend fun postLocation(@Body heartbeat: LocationHeartbeatDto): ApiEnvelope<Unit>

    @GET("api/v1/driver/nearby")
    suspend fun getNearbyDrivers(
        @Query("lat") latitude: Double,
        @Query("lng") longitude: Double
    ): ApiEnvelope<List<NearbyDriverDto>>
}
