package com.example.steelbikerunmobile.domain.usecase.trip

import com.example.steelbikerunmobile.BuildConfig
import com.example.steelbikerunmobile.data.remote.api.MapTilerApiService
import com.example.steelbikerunmobile.domain.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ReverseGeocodeUseCase @Inject constructor(
    private val mapTilerApiService: MapTilerApiService
) {
    suspend operator fun invoke(latLng: LatLng): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = mapTilerApiService.reverseGeocode(
                longitude = latLng.longitude,
                latitude = latLng.latitude,
                apiKey = BuildConfig.MAPTILER_API_KEY
            )
            val placeName = response.features.firstOrNull()?.placeName
            if (placeName != null) {
                Result.success(placeName)
            } else {
                Result.failure(Exception("No address found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
