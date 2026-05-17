package com.example.steelbikerunmobile.data.remote.api

import com.example.steelbikerunmobile.data.remote.dto.ApiEnvelope
import com.example.steelbikerunmobile.data.remote.dto.CreateTripRequestDto
import com.example.steelbikerunmobile.data.remote.dto.PriceEstimateDto
import com.example.steelbikerunmobile.data.remote.dto.PriceEstimateRequestDto
import com.example.steelbikerunmobile.data.remote.dto.TripResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface TripApiService {
    @POST("api/v1/trip/estimate")
    suspend fun estimate(@Body request: PriceEstimateRequestDto): ApiEnvelope<PriceEstimateDto>

    @POST("api/v1/trip")
    suspend fun createTrip(@Body request: CreateTripRequestDto): ApiEnvelope<TripResponseDto>

    @PUT("api/v1/trip/{id}/accept")
    suspend fun acceptTrip(@Path("id") tripId: String): ApiEnvelope<TripResponseDto>

    @PUT("api/v1/trip/{id}/arrive")
    suspend fun arriveAtPickup(@Path("id") tripId: String): ApiEnvelope<TripResponseDto>

    @PUT("api/v1/trip/{id}/start")
    suspend fun startTrip(@Path("id") tripId: String): ApiEnvelope<TripResponseDto>

    @PUT("api/v1/trip/{id}/complete")
    suspend fun completeTrip(@Path("id") tripId: String): ApiEnvelope<TripResponseDto>

    @PUT("api/v1/trip/{id}/cancel")
    suspend fun cancelTrip(@Path("id") tripId: String): ApiEnvelope<TripResponseDto>

    @GET("api/v1/trip/{id}")
    suspend fun getTrip(@Path("id") tripId: String): ApiEnvelope<TripResponseDto>

    @GET("api/v1/trip/history")
    suspend fun getHistory(@Query("role") role: String = "customer"): ApiEnvelope<List<TripResponseDto>>
}
