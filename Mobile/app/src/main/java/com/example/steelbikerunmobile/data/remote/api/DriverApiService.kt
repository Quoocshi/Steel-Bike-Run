package com.example.steelbikerunmobile.data.remote.api

import com.example.steelbikerunmobile.data.remote.dto.DriverProfileDto
import com.example.steelbikerunmobile.data.remote.dto.LocationHeartbeatDto
import com.example.steelbikerunmobile.data.remote.dto.NearbyDriverDto
import com.example.steelbikerunmobile.data.remote.dto.SwitchDriverRequestDto
import com.example.steelbikerunmobile.data.remote.dto.ApiEnvelope
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface DriverApiService {
    @GET("api/v1/driver/profile")
    suspend fun getProfile(): ApiEnvelope<DriverProfileDto>

    @POST("api/v1/driver/switch")
    suspend fun switchDriver(@Body request: SwitchDriverRequestDto?): ApiEnvelope<DriverProfileDto>

    @POST("api/v1/driver/location")
    suspend fun postLocation(@Body heartbeat: LocationHeartbeatDto): ApiEnvelope<Unit>

    @GET("api/v1/driver/nearby")
    suspend fun getNearbyDrivers(
        @Query("lat") latitude: Double,
        @Query("lng") longitude: Double
    ): ApiEnvelope<List<NearbyDriverDto>>
}
