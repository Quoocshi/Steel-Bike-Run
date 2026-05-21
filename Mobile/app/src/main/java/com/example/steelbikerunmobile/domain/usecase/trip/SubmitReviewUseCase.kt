package com.example.steelbikerunmobile.domain.usecase.trip

import com.example.steelbikerunmobile.domain.repository.TripRepository
import javax.inject.Inject

/**
 * Use case for submitting a trip review after completion.
 *
 * @param tripRepository Repository for trip operations
 */
class SubmitReviewUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    /**
     * Submit a review for a completed trip.
     *
     * @param tripId The ID of the trip being reviewed
     * @param rating Rating from 1 to 5 stars
     * @param comment Optional comment about the trip
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(tripId: String, rating: Int, comment: String?): Result<Unit> {
        return tripRepository.submitReview(tripId, rating, comment)
    }
}
