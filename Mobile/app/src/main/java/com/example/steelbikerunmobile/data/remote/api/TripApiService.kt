package com.example.steelbikerunmobile.data.remote.api

import com.example.steelbikerunmobile.data.remote.dto.ApiEnvelope
import com.example.steelbikerunmobile.data.remote.dto.CreateTripRequestDto
import com.example.steelbikerunmobile.data.remote.dto.PriceEstimateDto
import com.example.steelbikerunmobile.data.remote.dto.PriceEstimateRequestDto
import retrofit2.http.Body
import retrofit2.http.POST

interface TripApiService {
    @POST("api/v1/trip/estimate")
    suspend fun estimate(@Body request: PriceEstimateRequestDto): ApiEnvelope<PriceEstimateDto>

    @POST("api/v1/trip")
    suspend fun createTrip(@Body request: CreateTripRequestDto): ApiEnvelope<Unit>
}
