package com.example.steelbikerunmobile.domain.usecase.trip

import com.example.steelbikerunmobile.BuildConfig
import com.example.steelbikerunmobile.data.remote.api.GoongApiService
import com.example.steelbikerunmobile.domain.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SearchDestinationUseCase @Inject constructor(
    private val goongApiService: GoongApiService
) {
    suspend operator fun invoke(query: String): Result<List<Pair<String, LatLng>>> = withContext(Dispatchers.IO) {
        try {
            if (query.isBlank()) return@withContext Result.success(emptyList())
            
            // 1. Fetch autocomplete predictions (limit to 5 to avoid too many detail API calls)
            val autocompleteResponse = goongApiService.autocomplete(
                apiKey = BuildConfig.GOONG_API_KEY,
                input = query,
                limit = 5
            )
            
            val predictions = autocompleteResponse.predictions
            if (predictions.isEmpty()) {
                return@withContext Result.success(emptyList())
            }
            
            // 2. Concurrently fetch place details to get latitude & longitude
            val deferredDetails = predictions.take(5).map { prediction ->
                async {
                    try {
                        val detailResponse = goongApiService.placeDetail(
                            apiKey = BuildConfig.GOONG_API_KEY,
                            placeId = prediction.place_id
                        )
                        val loc = detailResponse.result.geometry.location
                        prediction.description to LatLng(loc.lat, loc.lng)
                    } catch (e: Exception) {
                        null // Ignore failed fetches
                    }
                }
            }
            
            val results = deferredDetails.awaitAll().filterNotNull()
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
