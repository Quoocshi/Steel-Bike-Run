package com.example.steelbikerunmobile.domain.repository

import com.example.steelbikerunmobile.domain.model.BookingDraft
import com.example.steelbikerunmobile.domain.model.PriceEstimate

interface TripRepository {
    suspend fun estimate(draft: BookingDraft): Result<PriceEstimate>
    suspend fun createTrip(draft: BookingDraft): Result<Unit>
}
