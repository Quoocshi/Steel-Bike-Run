package org.example.steelbikerunbackend.module.review.repository;

import org.example.steelbikerunbackend.module.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    /**
     * Check if a review already exists for a specific trip by a specific reviewer.
     */
    boolean existsByTripIdAndReviewerId(UUID tripId, UUID reviewerId);

    /**
     * Find review by trip ID and reviewer ID.
     */
    Optional<Review> findByTripIdAndReviewerId(UUID tripId, UUID reviewerId);

    /**
     * Find all reviews for a specific driver (reviewee).
     */
    List<Review> findByRevieweeIdOrderByCreatedAtDesc(UUID revieweeId);

    /**
     * Calculate average rating for a driver.
     */
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.reviewee.id = :driverId")
    Double findAverageRatingByDriverId(@Param("driverId") UUID driverId);

    /**
     * Count total reviews for a driver.
     */
    long countByRevieweeId(UUID revieweeId);

    /**
     * Find all reviews given by a user (reviewer).
     */
    List<Review> findByReviewerIdOrderByCreatedAtDesc(UUID reviewerId);
}
