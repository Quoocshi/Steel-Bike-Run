package org.example.steelbikerunbackend.module.review.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.steelbikerunbackend.common.enums.TripStatus;
import org.example.steelbikerunbackend.common.exception.AppException;
import org.example.steelbikerunbackend.common.exception.ErrorCode;
import org.example.steelbikerunbackend.module.driver.entity.Driver;
import org.example.steelbikerunbackend.module.driver.repository.DriverRepository;
import org.example.steelbikerunbackend.module.review.dto.ReviewResponse;
import org.example.steelbikerunbackend.module.review.dto.SubmitReviewRequest;
import org.example.steelbikerunbackend.module.review.entity.Review;
import org.example.steelbikerunbackend.module.review.repository.ReviewRepository;
import org.example.steelbikerunbackend.module.trip.entity.Trip;
import org.example.steelbikerunbackend.module.trip.repository.TripRepository;
import org.example.steelbikerunbackend.module.user.entity.User;
import org.example.steelbikerunbackend.module.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final DriverRepository driverRepository;

    /**
     * Submit a review for a completed trip.
     *
     * <p>Business rules:
     * <ul>
     *   <li>Only the customer (reviewer) of the trip can submit a review.</li>
     *   <li>Trip must be in COMPLETED status.</li>
     *   <li>Each customer can only review each trip once.</li>
     *   <li>After review is saved, the driver's average rating is recalculated.</li>
     * </ul>
     *
     * @param reviewerEmail email of the customer submitting the review
     * @param request      review details (tripId, rating, comment)
     * @return ReviewResponse with the saved review details
     */
    @Transactional
    public ReviewResponse submitReview(String reviewerEmail, SubmitReviewRequest request) {
        User reviewer = userRepository.findByEmail(reviewerEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        UUID tripId = UUID.fromString(request.tripId());
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new AppException(ErrorCode.TRIP_NOT_FOUND));

        // Validate trip status
        if (trip.getStatus() != TripStatus.COMPLETED) {
            throw new AppException(ErrorCode.REVIEW_TRIP_NOT_COMPLETED);
        }

        // Validate reviewer is the customer of this trip
        if (!trip.getCustomer().getId().equals(reviewer.getId())) {
            throw new AppException(ErrorCode.DRIVER_NOT_AUTHORIZED,
                    "Only the customer of this trip can submit a review.");
        }

        // Check for duplicate review
        if (reviewRepository.existsByTripIdAndReviewerId(tripId, reviewer.getId())) {
            throw new AppException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        // Validate driver exists for this trip
        if (trip.getDriver() == null) {
            throw new AppException(ErrorCode.DRIVER_NOT_FOUND,
                    "Cannot review a trip without an assigned driver.");
        }

        User reviewee = trip.getDriver().getUser();
        Driver driver = trip.getDriver();

        // Create and save the review
        Review review = Review.builder()
                .trip(trip)
                .reviewer(reviewer)
                .reviewee(reviewee)
                .rating(request.rating())
                .comment(request.comment())
                .build();

        review = reviewRepository.save(review);
        log.info("[Review] Customer {} rated trip {} (driver {}): {} stars",
                reviewerEmail, tripId, driver.getId(), request.rating());

        // Update driver's average rating
        updateDriverRating(driver);

        return toResponse(review);
    }

    /**
     * Get a review by trip ID.
     *
     * @param tripId the trip ID
     * @return ReviewResponse or null if not found
     */
    public ReviewResponse getReviewByTripId(String tripId) {
        UUID id = UUID.fromString(tripId);
        return reviewRepository.findById(id)
                .map(this::toResponse)
                .orElse(null);
    }

    /**
     * Get all reviews for a driver.
     *
     * @param driverId the driver ID
     * @return list of reviews
     */
    public java.util.List<ReviewResponse> getReviewsForDriver(UUID driverId) {
        return reviewRepository.findByRevieweeIdOrderByCreatedAtDesc(driverId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Calculate and update the driver's average rating based on all their reviews.
     */
    private void updateDriverRating(Driver driver) {
        Double avgRating = reviewRepository.findAverageRatingByDriverId(driver.getId());
        if (avgRating != null) {
            // Round to 1 decimal place
            float newRating = (float) (Math.round(avgRating * 10.0) / 10.0);
            driver.setRating(newRating);
            driverRepository.save(driver);
            log.info("[Review] Updated driver {} average rating to {}", driver.getId(), newRating);
        }
    }

    private ReviewResponse toResponse(Review review) {
        return new ReviewResponse(
                review.getId().toString(),
                review.getTrip().getId().toString(),
                review.getReviewer().getId().toString(),
                review.getReviewee().getId().toString(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }
}
