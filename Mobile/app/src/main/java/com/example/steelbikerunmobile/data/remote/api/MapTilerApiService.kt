package com.example.steelbikerunmobile.data.remote.api

import com.example.steelbikerunmobile.data.remote.model.maptiler.MapTilerGeocodingResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MapTilerApiService {
    @GET("geocoding/{query}.json")
    suspend fun searchDestinations(
        @Path("query") query: String,
        @Query("key") apiKey: String,
        @Query("country") country: String = "vn",
        @Query("language") language: String = "vi",
        @Query("limit") limit: Int = 10
    ): MapTilerGeocodingResponse

    @GET("geocoding/{longitude},{latitude}.json")
    suspend fun reverseGeocode(
        @Path("longitude") longitude: Double,
        @Path("latitude") latitude: Double,
        @Query("key") apiKey: String,
        @Query("language") language: String = "vi",
        @Query("limit") limit: Int = 1
    ): MapTilerGeocodingResponse
}
