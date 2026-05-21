package org.example.steelbikerunbackend.module.review;

import org.example.steelbikerunbackend.common.enums.TripStatus;
import org.example.steelbikerunbackend.common.exception.AppException;
import org.example.steelbikerunbackend.module.driver.entity.Driver;
import org.example.steelbikerunbackend.module.driver.repository.DriverRepository;
import org.example.steelbikerunbackend.module.review.dto.ReviewResponse;
import org.example.steelbikerunbackend.module.review.dto.SubmitReviewRequest;
import org.example.steelbikerunbackend.module.review.entity.Review;
import org.example.steelbikerunbackend.module.review.repository.ReviewRepository;
import org.example.steelbikerunbackend.module.review.service.ReviewService;
import org.example.steelbikerunbackend.module.trip.entity.Trip;
import org.example.steelbikerunbackend.module.trip.repository.TripRepository;
import org.example.steelbikerunbackend.module.user.entity.User;
import org.example.steelbikerunbackend.module.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private TripRepository tripRepository;
    @Mock private UserRepository userRepository;
    @Mock private DriverRepository driverRepository;

    @InjectMocks private ReviewService reviewService;

    private User customer;
    private User driverUser;
    private Driver driver;
    private Trip completedTrip;

    @BeforeEach
    void setUp() {
        customer = User.builder()
                .id(UUID.randomUUID())
                .email("customer@test.com")
                .fullName("Test Customer")
                .build();

        driverUser = User.builder()
                .id(UUID.randomUUID())
                .email("driver@test.com")
                .fullName("Test Driver")
                .build();

        driver = Driver.builder()
                .id(UUID.randomUUID())
                .user(driverUser)
                .vehiclePlate("51G-999.99")
                .vehicleModel("Honda Wave")
                .vehicleColor("Blue")
                .rating(4.5f)
                .totalTrips(50)
                .isOnline(false)
                .build();

        completedTrip = Trip.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .driver(driver)
                .pickupLat(10.7769)
                .pickupLng(106.7009)
                .pickupH3Index("891f1d4b2a3ffff")
                .destLat(10.8230)
                .destLng(106.6297)
                .destAddress("San bay Tan Son Nhat")
                .status(TripStatus.COMPLETED)
                .basePrice(new BigDecimal("55000"))
                .surgeMultiplier(BigDecimal.ONE)
                .finalPrice(new BigDecimal("55000"))
                .distanceKm(8.5f)
                .durationMinutes(26)
                .requestedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("submitReview()")
    class SubmitReviewTests {

        @Test
        @DisplayName("submitReview: COMPLETED trip -> review saved + driver rating updated")
        void submitReview_Success() {
            SubmitReviewRequest request = new SubmitReviewRequest(
                    completedTrip.getId().toString(), 5, "Tài xế rất thân thiện"
            );

            when(userRepository.findByEmail("customer@test.com")).thenReturn(Optional.of(customer));
            when(tripRepository.findById(completedTrip.getId())).thenReturn(Optional.of(completedTrip));
            when(reviewRepository.existsByTripIdAndReviewerId(completedTrip.getId(), customer.getId())).thenReturn(false);
            when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
                Review r = inv.getArgument(0);
                r.setId(UUID.randomUUID());
                r.setCreatedAt(LocalDateTime.now());
                return r;
            });
            when(reviewRepository.findAverageRatingByDriverId(driver.getId())).thenReturn(4.8);

            ReviewResponse result = reviewService.submitReview("customer@test.com", request);

            assertThat(result).isNotNull();
            assertThat(result.rating()).isEqualTo(5);
            assertThat(result.comment()).isEqualTo("Tài xế rất thân thiện");
            assertThat(result.revieweeId()).isEqualTo(driverUser.getId().toString());
            assertThat(result.reviewerId()).isEqualTo(customer.getId().toString());
            verify(driverRepository).save(driver);
        }

        @Test
        @DisplayName("submitReview: Trip not COMPLETED -> throws REVIEW_TRIP_NOT_COMPLETED")
        void submitReview_TripNotCompleted_ThrowsException() {
            completedTrip.setStatus(TripStatus.IN_PROGRESS);
            SubmitReviewRequest request = new SubmitReviewRequest(
                    completedTrip.getId().toString(), 5, "Good driver"
            );

            when(userRepository.findByEmail("customer@test.com")).thenReturn(Optional.of(customer));
            when(tripRepository.findById(completedTrip.getId())).thenReturn(Optional.of(completedTrip));

            assertThatThrownBy(() -> reviewService.submitReview("customer@test.com", request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                            .isEqualTo(org.example.steelbikerunbackend.common.exception.ErrorCode.REVIEW_TRIP_NOT_COMPLETED));
        }

        @Test
        @DisplayName("submitReview: Duplicate review -> throws REVIEW_ALREADY_EXISTS")
        void submitReview_Duplicate_ThrowsException() {
            SubmitReviewRequest request = new SubmitReviewRequest(
                    completedTrip.getId().toString(), 5, "Good driver"
            );

            when(userRepository.findByEmail("customer@test.com")).thenReturn(Optional.of(customer));
            when(tripRepository.findById(completedTrip.getId())).thenReturn(Optional.of(completedTrip));
            when(reviewRepository.existsByTripIdAndReviewerId(completedTrip.getId(), customer.getId())).thenReturn(true);

            assertThatThrownBy(() -> reviewService.submitReview("customer@test.com", request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                            .isEqualTo(org.example.steelbikerunbackend.common.exception.ErrorCode.REVIEW_ALREADY_EXISTS));
        }

        @Test
        @DisplayName("submitReview: Non-customer tries to review -> throws DRIVER_NOT_AUTHORIZED")
        void submitReview_WrongCustomer_ThrowsException() {
            User otherCustomer = User.builder()
                    .id(UUID.randomUUID())
                    .email("other@test.com")
                    .fullName("Other Customer")
                    .build();

            SubmitReviewRequest request = new SubmitReviewRequest(
                    completedTrip.getId().toString(), 5, "Good driver"
            );

            when(userRepository.findByEmail("other@test.com")).thenReturn(Optional.of(otherCustomer));
            when(tripRepository.findById(completedTrip.getId())).thenReturn(Optional.of(completedTrip));

            assertThatThrownBy(() -> reviewService.submitReview("other@test.com", request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                            .isEqualTo(org.example.steelbikerunbackend.common.exception.ErrorCode.DRIVER_NOT_AUTHORIZED));
        }

        @Test
        @DisplayName("submitReview: Trip not found -> throws TRIP_NOT_FOUND")
        void submitReview_TripNotFound_ThrowsException() {
            UUID fakeTripId = UUID.randomUUID();
            SubmitReviewRequest request = new SubmitReviewRequest(
                    fakeTripId.toString(), 5, "Good driver"
            );

            when(userRepository.findByEmail("customer@test.com")).thenReturn(Optional.of(customer));
            when(tripRepository.findById(fakeTripId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewService.submitReview("customer@test.com", request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                            .isEqualTo(org.example.steelbikerunbackend.common.exception.ErrorCode.TRIP_NOT_FOUND));
        }

        @Test
        @DisplayName("submitReview: Trip without driver -> throws DRIVER_NOT_FOUND")
        void submitReview_NoDriver_ThrowsException() {
            completedTrip.setDriver(null);
            SubmitReviewRequest request = new SubmitReviewRequest(
                    completedTrip.getId().toString(), 5, "Good driver"
            );

            when(userRepository.findByEmail("customer@test.com")).thenReturn(Optional.of(customer));
            when(tripRepository.findById(completedTrip.getId())).thenReturn(Optional.of(completedTrip));
            when(reviewRepository.existsByTripIdAndReviewerId(completedTrip.getId(), customer.getId())).thenReturn(false);

            assertThatThrownBy(() -> reviewService.submitReview("customer@test.com", request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                            .isEqualTo(org.example.steelbikerunbackend.common.exception.ErrorCode.DRIVER_NOT_FOUND));
        }

        @Test
        @DisplayName("submitReview: Driver rating updated correctly")
        void submitReview_DriverRatingUpdated() {
            SubmitReviewRequest request = new SubmitReviewRequest(
                    completedTrip.getId().toString(), 5, "Excellent!"
            );

            when(userRepository.findByEmail("customer@test.com")).thenReturn(Optional.of(customer));
            when(tripRepository.findById(completedTrip.getId())).thenReturn(Optional.of(completedTrip));
            when(reviewRepository.existsByTripIdAndReviewerId(completedTrip.getId(), customer.getId())).thenReturn(false);
            when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
                Review r = inv.getArgument(0);
                r.setId(UUID.randomUUID());
                r.setCreatedAt(LocalDateTime.now());
                return r;
            });
            when(reviewRepository.findAverageRatingByDriverId(driver.getId())).thenReturn(4.8333);

            reviewService.submitReview("customer@test.com", request);

            // Verify driver rating was rounded to 1 decimal place (4.8)
            assertThat(driver.getRating()).isEqualTo(4.8f);
            verify(driverRepository).save(driver);
        }
    }

    @Nested
    @DisplayName("getReviewByTripId()")
    class GetReviewByTripIdTests {

        @Test
        @DisplayName("getReviewByTripId: Review exists -> returns review")
        void getReviewByTripId_Exists_ReturnsReview() {
            Review review = Review.builder()
                    .id(UUID.randomUUID())
                    .trip(completedTrip)
                    .reviewer(customer)
                    .reviewee(driverUser)
                    .rating(5)
                    .comment("Great!")
                    .createdAt(LocalDateTime.now())
                    .build();

            when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

            ReviewResponse result = reviewService.getReviewByTripId(review.getId().toString());

            assertThat(result).isNotNull();
            assertThat(result.rating()).isEqualTo(5);
        }

        @Test
        @DisplayName("getReviewByTripId: Review not exists -> returns null")
        void getReviewByTripId_NotExists_ReturnsNull() {
            UUID reviewId = UUID.randomUUID();
            when(reviewRepository.findById(reviewId)).thenReturn(Optional.empty());

            ReviewResponse result = reviewService.getReviewByTripId(reviewId.toString());

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("getReviewsForDriver()")
    class GetReviewsForDriverTests {

        @Test
        @DisplayName("getReviewsForDriver: Returns list of reviews")
        void getReviewsForDriver_ReturnsReviews() {
            Review review = Review.builder()
                    .id(UUID.randomUUID())
                    .trip(completedTrip)
                    .reviewer(customer)
                    .reviewee(driverUser)
                    .rating(5)
                    .comment("Great!")
                    .createdAt(LocalDateTime.now())
                    .build();

            when(reviewRepository.findByRevieweeIdOrderByCreatedAtDesc(driver.getId()))
                    .thenReturn(java.util.List.of(review));

            var result = reviewService.getReviewsForDriver(driver.getId());

            assertThat(result).hasSize(1);
            assertThat(result.get(0).rating()).isEqualTo(5);
        }
    }
}
