package com.example.steelbikerunmobile.domain.usecase.trip

import com.example.steelbikerunmobile.domain.model.BookingDraft
import com.example.steelbikerunmobile.domain.repository.TripRepository
import javax.inject.Inject

class GetPriceEstimateUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    suspend operator fun invoke(draft: BookingDraft) = tripRepository.estimate(draft)
}
