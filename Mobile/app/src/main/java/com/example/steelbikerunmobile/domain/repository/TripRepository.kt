package com.example.steelbikerunmobile.domain.repository

import com.example.steelbikerunmobile.domain.model.BookingDraft
import com.example.steelbikerunmobile.domain.model.PriceEstimate
import com.example.steelbikerunmobile.data.remote.dto.TripResponseDto

interface TripRepository {
    suspend fun estimate(draft: BookingDraft): Result<PriceEstimate>
    suspend fun createTrip(draft: BookingDraft): Result<String>  // Returns tripId
    suspend fun acceptTrip(tripId: String): Result<Unit>
    suspend fun arriveAtPickup(tripId: String): Result<Unit>
    suspend fun startTrip(tripId: String): Result<Unit>
    suspend fun completeTrip(tripId: String): Result<TripResponseDto>
    suspend fun getTrip(tripId: String): Result<TripResponseDto>
    suspend fun cancelTrip(tripId: String): Result<Unit>
    suspend fun submitReview(tripId: String, rating: Int, comment: String?): Result<Unit>
}
