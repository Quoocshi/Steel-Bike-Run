package com.example.steelbikerunmobile.domain.usecase.trip

import com.example.steelbikerunmobile.BuildConfig
import com.example.steelbikerunmobile.data.remote.api.MapTilerApiService
import com.example.steelbikerunmobile.domain.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SearchDestinationUseCase @Inject constructor(
    private val mapTilerApiService: MapTilerApiService
) {
    suspend operator fun invoke(query: String): Result<List<Pair<String, LatLng>>> = withContext(Dispatchers.IO) {
        try {
            if (query.isBlank()) return@withContext Result.success(emptyList())
            
            val response = mapTilerApiService.searchDestinations(
                query = query,
                apiKey = BuildConfig.MAPTILER_API_KEY
            )
            
            val results = response.features.mapNotNull { feature ->
                val center = feature.center ?: return@mapNotNull null
                if (center.size >= 2) {
                    // MapTiler center is [longitude, latitude]
                    val lng = center[0]
                    val lat = center[1]
                    val name = feature.placeName
                    name to LatLng(lat, lng)
                } else {
                    null
                }
            }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
