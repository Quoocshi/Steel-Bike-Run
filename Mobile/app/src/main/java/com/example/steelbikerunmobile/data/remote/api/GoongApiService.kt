package com.example.steelbikerunmobile.data.remote.api

import com.example.steelbikerunmobile.data.remote.model.goong.GoongAutoCompleteResponse
import com.example.steelbikerunmobile.data.remote.model.goong.GoongPlaceDetailResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface GoongApiService {
    @GET("Place/AutoComplete")
    suspend fun autocomplete(
        @Query("api_key") apiKey: String,
        @Query("input") input: String,
        @Query("limit") limit: Int = 10
    ): GoongAutoCompleteResponse

    @GET("Place/Detail")
    suspend fun placeDetail(
        @Query("api_key") apiKey: String,
        @Query("place_id") placeId: String
    ): GoongPlaceDetailResponse
}
