package org.example.steelbikerunbackend.module.trip;

import org.example.steelbikerunbackend.common.enums.TripStatus;
import org.example.steelbikerunbackend.common.exception.AppException;
import org.example.steelbikerunbackend.module.driver.entity.Driver;
import org.example.steelbikerunbackend.module.driver.repository.DriverRepository;
import org.example.steelbikerunbackend.module.trip.dto.CreateTripRequest;
import org.example.steelbikerunbackend.module.trip.dto.TripResponse;
import org.example.steelbikerunbackend.module.trip.entity.Trip;
import org.example.steelbikerunbackend.module.trip.repository.TripRepository;
import org.example.steelbikerunbackend.module.trip.service.PricingService;
import org.example.steelbikerunbackend.module.trip.service.TripService;
import org.example.steelbikerunbackend.module.user.entity.User;
import org.example.steelbikerunbackend.module.user.repository.UserRepository;
import org.example.steelbikerunbackend.module.websocket.MatchingService;
import org.example.steelbikerunbackend.module.websocket.matching.MatchingQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
class TripServiceTest {

    @Mock private TripRepository tripRepository;
    @Mock private UserRepository userRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private PricingService pricingService;
    @Mock private MatchingService matchingService;
    @Mock private MatchingQueue matchingQueue;

    @InjectMocks private TripService tripService;

    private User customer;
    private User driverUser;
    private Driver driver;
    private Trip requestedTrip;
    private Trip acceptedTrip;
    private Trip arrivedTrip;
    private Trip inProgressTrip;

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
                .rating(4.8f)
                .totalTrips(100)
                .isOnline(true)
                .build();

        requestedTrip = Trip.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .pickupLat(10.7769)
                .pickupLng(106.7009)
                .pickupH3Index("891f1d4b2a3ffff")
                .destLat(10.8230)
                .destLng(106.6297)
                .destAddress("San bay Tan Son Nhat")
                .status(TripStatus.REQUESTED)
                .basePrice(new BigDecimal("55000"))
                .surgeMultiplier(BigDecimal.ONE)
                .finalPrice(new BigDecimal("55000"))
                .distanceKm(8.5f)
                .durationMinutes(26)
                .requestedAt(LocalDateTime.now())
                .build();

        acceptedTrip = Trip.builder()
                .id(requestedTrip.getId())
                .customer(customer)
                .driver(driver)
                .pickupLat(10.7769)
                .pickupLng(106.7009)
                .pickupH3Index("891f1d4b2a3ffff")
                .destLat(10.8230)
                .destLng(106.6297)
                .destAddress("San bay Tan Son Nhat")
                .status(TripStatus.ACCEPTED)
                .basePrice(new BigDecimal("55000"))
                .surgeMultiplier(BigDecimal.ONE)
                .finalPrice(new BigDecimal("55000"))
                .distanceKm(8.5f)
                .durationMinutes(26)
                .requestedAt(LocalDateTime.now())
                .acceptedAt(LocalDateTime.now())
                .build();

        arrivedTrip = Trip.builder()
                .id(requestedTrip.getId())
                .customer(customer)
                .driver(driver)
                .pickupLat(10.7769)
                .pickupLng(106.7009)
                .pickupH3Index("891f1d4b2a3ffff")
                .destLat(10.8230)
                .destLng(106.6297)
                .destAddress("San bay Tan Son Nhat")
                .status(TripStatus.ARRIVED)
                .basePrice(new BigDecimal("55000"))
                .surgeMultiplier(BigDecimal.ONE)
                .finalPrice(new BigDecimal("55000"))
                .distanceKm(8.5f)
                .durationMinutes(26)
                .requestedAt(LocalDateTime.now())
                .acceptedAt(LocalDateTime.now())
                .build();

        inProgressTrip = Trip.builder()
                .id(requestedTrip.getId())
                .customer(customer)
                .driver(driver)
                .pickupLat(10.7769)
                .pickupLng(106.7009)
                .pickupH3Index("891f1d4b2a3ffff")
                .destLat(10.8230)
                .destLng(106.6297)
                .destAddress("San bay Tan Son Nhat")
                .status(TripStatus.IN_PROGRESS)
                .basePrice(new BigDecimal("55000"))
                .surgeMultiplier(BigDecimal.ONE)
                .finalPrice(new BigDecimal("55000"))
                .distanceKm(8.5f)
                .durationMinutes(26)
                .requestedAt(LocalDateTime.now())
                .acceptedAt(LocalDateTime.now())
                .startedAt(LocalDateTime.now())
                .build();
    }

    // -------------------------------------------------------------------------
    // acceptTrip()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("acceptTrip: REQUESTED -> ACCEPTED thành công")
    void acceptTrip_Success() {
        when(tripRepository.findById(requestedTrip.getId())).thenReturn(Optional.of(requestedTrip));
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driverUser));
        when(driverRepository.findByUserIdWithUser(driverUser.getId())).thenReturn(Optional.of(driver));
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

        TripResponse result = tripService.acceptTrip("driver@test.com", requestedTrip.getId());

        assertThat(result.status()).isEqualTo(TripStatus.ACCEPTED);
        assertThat(result.driverId()).isEqualTo(driver.getId().toString());
        assertThat(result.acceptedAt()).isNotNull();
        verify(matchingService).notifyCustomerDriverFound(any());
        verify(matchingService).broadcastTripStatus(any(), eq("Tài xế đang đến điểm đón"));
    }

    @Test
    @DisplayName("acceptTrip: Trip không ở REQUESTED -> ném TRIP_ALREADY_ACCEPTED")
    void acceptTrip_AlreadyAccepted_ThrowsException() {
        acceptedTrip.setStatus(TripStatus.ACCEPTED);
        when(tripRepository.findById(acceptedTrip.getId())).thenReturn(Optional.of(acceptedTrip));

        assertThatThrownBy(() -> tripService.acceptTrip("driver@test.com", acceptedTrip.getId()))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("acceptTrip: Trip không tồn tại -> ném TRIP_NOT_FOUND")
    void acceptTrip_TripNotFound_ThrowsException() {
        UUID fakeId = UUID.randomUUID();
        when(tripRepository.findById(fakeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.acceptTrip("driver@test.com", fakeId))
                .isInstanceOf(AppException.class);
    }

    // -------------------------------------------------------------------------
    // arriveAtPickup()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("arriveAtPickup: ACCEPTED -> ARRIVED thành công")
    void arriveAtPickup_Success() {
        when(tripRepository.findById(acceptedTrip.getId())).thenReturn(Optional.of(acceptedTrip));
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driverUser));
        when(driverRepository.findByUserIdWithUser(driverUser.getId())).thenReturn(Optional.of(driver));
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

        TripResponse result = tripService.arriveAtPickup("driver@test.com", acceptedTrip.getId());

        assertThat(result.status()).isEqualTo(TripStatus.ARRIVED);
        verify(matchingService).broadcastTripStatus(any(), eq("Tài xế đã đến điểm đón"));
    }

    @Test
    @DisplayName("arriveAtPickup: Trip không phải ACCEPTED -> ném INVALID_TRIP_STATUS")
    void arriveAtPickup_WrongStatus_ThrowsException() {
        when(tripRepository.findById(arrivedTrip.getId())).thenReturn(Optional.of(arrivedTrip));
        // NOTE: no need to stub userRepository/driverRepository here because
        // findTripAndValidateDriver() throws INVALID_TRIP_STATUS before reaching driver lookup.

        assertThatThrownBy(() -> tripService.arriveAtPickup("driver@test.com", arrivedTrip.getId()))
                .isInstanceOf(AppException.class);
    }

    // -------------------------------------------------------------------------
    // startTrip()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("startTrip: ARRIVED -> IN_PROGRESS thành công")
    void startTrip_Success() {
        when(tripRepository.findById(arrivedTrip.getId())).thenReturn(Optional.of(arrivedTrip));
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driverUser));
        when(driverRepository.findByUserIdWithUser(driverUser.getId())).thenReturn(Optional.of(driver));
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

        TripResponse result = tripService.startTrip("driver@test.com", arrivedTrip.getId());

        assertThat(result.status()).isEqualTo(TripStatus.IN_PROGRESS);
        assertThat(result.startedAt()).isNotNull();
        verify(matchingService).broadcastTripStatus(any(), eq("Chuyến đi đã bắt đầu"));
    }

    @Test
    @DisplayName("startTrip: Không phải driver của trip -> ném DRIVER_NOT_AUTHORIZED")
    void startTrip_WrongDriver_ThrowsException() {
        Driver otherDriver = Driver.builder().id(UUID.randomUUID()).user(driverUser).build();
        when(tripRepository.findById(arrivedTrip.getId())).thenReturn(Optional.of(arrivedTrip));
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driverUser));
        when(driverRepository.findByUserIdWithUser(driverUser.getId())).thenReturn(Optional.of(otherDriver));

        assertThatThrownBy(() -> tripService.startTrip("driver@test.com", arrivedTrip.getId()))
                .isInstanceOf(AppException.class);
    }

    // -------------------------------------------------------------------------
    // completeTrip()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("completeTrip: IN_PROGRESS -> COMPLETED + tăng totalTrips")
    void completeTrip_Success_IncrementsTotalTrips() {
        int previousTrips = driver.getTotalTrips();
        when(tripRepository.findById(inProgressTrip.getId())).thenReturn(Optional.of(inProgressTrip));
        when(userRepository.findByEmail("driver@test.com")).thenReturn(Optional.of(driverUser));
        when(driverRepository.findByUserIdWithUser(driverUser.getId())).thenReturn(Optional.of(driver));
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

        TripResponse result = tripService.completeTrip("driver@test.com", inProgressTrip.getId());

        assertThat(result.status()).isEqualTo(TripStatus.COMPLETED);
        assertThat(result.completedAt()).isNotNull();
        assertThat(driver.getTotalTrips()).isEqualTo(previousTrips + 1);
        verify(driverRepository).save(driver);
    }

    // -------------------------------------------------------------------------
    // cancelTrip()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("cancelTrip: REQUESTED -> CANCELLED thành công")
    void cancelTrip_FromRequested_Success() {
        when(tripRepository.findById(requestedTrip.getId())).thenReturn(Optional.of(requestedTrip));
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

        TripResponse result = tripService.cancelTrip("customer@test.com", requestedTrip.getId());

        assertThat(result.status()).isEqualTo(TripStatus.CANCELLED);
        verify(matchingService).broadcastTripStatus(any(), eq("Cuốc xe đã bị hủy"));
    }

    @Test
    @DisplayName("cancelTrip: COMPLETED -> ném INVALID_TRIP_STATUS")
    void cancelTrip_FromCompleted_ThrowsException() {
        Trip completedTrip = Trip.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .status(TripStatus.COMPLETED)
                .build();
        when(tripRepository.findById(completedTrip.getId())).thenReturn(Optional.of(completedTrip));

        assertThatThrownBy(() -> tripService.cancelTrip("customer@test.com", completedTrip.getId()))
                .isInstanceOf(AppException.class);
    }

    // -------------------------------------------------------------------------
    // getTrip()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getTrip: Trả về TripResponse đầy đủ")
    void getTrip_Success() {
        when(tripRepository.findById(requestedTrip.getId())).thenReturn(Optional.of(requestedTrip));

        TripResponse result = tripService.getTrip(requestedTrip.getId());

        assertThat(result.id()).isEqualTo(requestedTrip.getId().toString());
        assertThat(result.customerId()).isEqualTo(customer.getId().toString());
        assertThat(result.finalPrice()).isEqualByComparingTo(new BigDecimal("55000"));
    }
}
