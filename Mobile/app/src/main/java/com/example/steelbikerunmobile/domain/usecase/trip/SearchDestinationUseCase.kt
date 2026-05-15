package com.example.steelbikerunmobile.domain.usecase.trip

import com.example.steelbikerunmobile.data.remote.api.NominatimApiService
import com.example.steelbikerunmobile.domain.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SearchDestinationUseCase @Inject constructor(
    private val nominatimApiService: NominatimApiService
) {
    suspend operator fun invoke(query: String): Result<List<Pair<String, LatLng>>> = withContext(Dispatchers.IO) {
        try {
            if (query.isBlank()) return@withContext Result.success(emptyList())
            
            val response = nominatimApiService.searchDestinations(query = query)
            
            val results = response.mapNotNull { place ->
                val lat = place.lat.toDoubleOrNull()
                val lon = place.lon.toDoubleOrNull()
                if (lat != null && lon != null) {
                    val name = place.name.ifBlank { place.display_name.substringBefore(",") }
                    name to LatLng(lat, lon)
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
