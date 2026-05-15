package com.example.steelbikerunmobile.data.remote.api

import com.example.steelbikerunmobile.data.remote.model.nominatim.NominatimPlace
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface NominatimApiService {
    @Headers("User-Agent: SteelBikeRun/1.0")
    @GET("search")
    suspend fun searchDestinations(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressdetails: Int = 1,
        @Query("limit") limit: Int = 10,
        @Query("countrycodes") countrycodes: String = "vn"
    ): List<NominatimPlace>
}
