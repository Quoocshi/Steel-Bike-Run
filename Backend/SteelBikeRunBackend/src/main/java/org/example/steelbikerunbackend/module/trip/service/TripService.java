package org.example.steelbikerunbackend.module.trip.service;

import com.uber.h3core.H3Core;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.steelbikerunbackend.common.enums.TripStatus;
import org.example.steelbikerunbackend.common.exception.AppException;
import org.example.steelbikerunbackend.common.exception.ErrorCode;
import org.example.steelbikerunbackend.module.driver.entity.Driver;
import org.example.steelbikerunbackend.module.driver.repository.DriverRepository;
import org.example.steelbikerunbackend.module.trip.dto.CreateTripRequest;
import org.example.steelbikerunbackend.module.trip.dto.TripResponse;
import org.example.steelbikerunbackend.module.trip.entity.Trip;
import org.example.steelbikerunbackend.module.trip.repository.TripRepository;
import org.example.steelbikerunbackend.module.user.entity.User;
import org.example.steelbikerunbackend.module.user.repository.UserRepository;
import org.example.steelbikerunbackend.module.websocket.MatchingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * TripService — quản lý toàn bộ vòng đời cuốc xe.
 *
 * <h3>State machine:</h3>
 * 
 * <pre>
 * REQUESTED --> ACCEPTED --> ARRIVED --> IN_PROGRESS --> COMPLETED
 *      |            |            |              |
 *      +------------+------------+--------------+--------> CANCELLED
 * </pre>
 *
 * <h3>Nguyên tắc thiết kế:</h3>
 * <ul>
 * <li>Giá snapshot tại thời điểm đặt xe — KHÔNG thay đổi sau đó.</li>
 * <li>Mỗi transition cập nhật DB + broadcast WebSocket.</li>
 * <li>accept() sử dụng optimistic check để tránh race condition khi 2 driver
 * cùng nhận.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripService {

        private final TripRepository tripRepository;
        private final UserRepository userRepository;
        private final DriverRepository driverRepository;
        private final PricingService pricingService;
        private final MatchingService matchingService;

        // -------------------------------------------------------------------------
        // CREATE: Customer đặt xe
        // -------------------------------------------------------------------------

        /**
         * Tạo cuốc xe mới và broadcast đến tài xế gần nhất qua WebSocket.
         *
         * <p>
         * <b>Flow:</b>
         * <ol>
         * <li>Validate customer tồn tại.</li>
         * <li>Tính giá (tái sử dụng PricingService) + snapshot surge tại thời điểm
         * đặt.</li>
         * <li>Lưu Trip vào DB với trạng thái REQUESTED.</li>
         * <li>Gọi MatchingService broadcast đến top 3 driver gần nhất.</li>
         * </ol>
         *
         * @param customerEmail email từ JWT principal
         * @param request       tọa độ điểm đón + điểm đến
         * @return TripResponse với đầy đủ thông tin cuốc xe vừa tạo
         */
        @Transactional
        public TripResponse createTrip(String customerEmail, CreateTripRequest request) {
                User customer = userRepository.findByEmail(customerEmail)
                                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

                H3Core h3 = pricingService.getH3Core();

                // Tính H3 cell điểm đón
                String pickupH3 = h3.latLngToCellAddress(request.pickupLat(), request.pickupLng(), 9);

                // Tính giá (snapshot tại thời điểm đặt)
                double distanceKm = pricingService.estimateDistanceKm(
                                request.pickupLat(), request.pickupLng(),
                                request.destLat(), request.destLng());
                int durationMinutes = pricingService.estimateDurationMinutes(distanceKm);
                BigDecimal basePrice = pricingService.calculateBasePrice(distanceKm);
                BigDecimal surge = pricingService.getSurgeMultiplier(pickupH3, h3);
                BigDecimal finalPrice = basePrice.multiply(surge)
                                .divide(new BigDecimal("1000"), 0, java.math.RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("1000"));

                // Tạo Trip entity
                Trip trip = Trip.builder()
                                .customer(customer)
                                .pickupLat(request.pickupLat())
                                .pickupLng(request.pickupLng())
                                .pickupH3Index(pickupH3)
                                .destLat(request.destLat())
                                .destLng(request.destLng())
                                .destAddress(request.destAddress())
                                .status(TripStatus.REQUESTED)
                                .basePrice(basePrice)
                                .surgeMultiplier(surge)
                                .finalPrice(finalPrice)
                                .distanceKm((float) distanceKm)
                                .durationMinutes(durationMinutes)
                                .build();

                trip = tripRepository.save(trip);

                log.info("[Trip] Created trip {} for customer {} | price={} surge={} | pickup=[{},{}]",
                                trip.getId(), customer.getEmail(), finalPrice, surge,
                                request.pickupLat(), request.pickupLng());

                // Broadcast đến tài xế gần nhất qua WebSocket
                int notifiedDrivers = matchingService.broadcastToNearbyDrivers(trip);
                if (notifiedDrivers == 0) {
                        log.warn("[Trip] Trip {} - Không có driver nào gần. Customer sẽ phải chờ.", trip.getId());
                }

                return toResponse(trip);
        }

        // -------------------------------------------------------------------------
        // ACCEPT: Driver nhận cuốc
        // -------------------------------------------------------------------------

        /**
         * Driver accept cuốc xe. Chỉ trip REQUESTED mới accept được.
         * Nếu 2 driver gọi đồng thời, driver đến trước sẽ thắng.
         *
         * @param driverEmail email từ JWT principal
         * @param tripId      ID cuốc xe
         * @return TripResponse đã cập nhật driver info
         */
        @Transactional
        public TripResponse acceptTrip(String driverEmail, UUID tripId) {
                Trip trip = tripRepository.findById(tripId)
                                .orElseThrow(() -> new AppException(ErrorCode.TRIP_NOT_FOUND));

                if (trip.getStatus() != TripStatus.REQUESTED) {
                        throw new AppException(ErrorCode.TRIP_ALREADY_ACCEPTED,
                                        "Cuốc xe đã được nhận bởi tài xế khác.");
                }

                User user = userRepository.findByEmail(driverEmail)
                                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
                Driver driver = driverRepository.findByUserIdWithUser(user.getId())
                                .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));

                // Cập nhật trip
                trip.setDriver(driver);
                trip.setStatus(TripStatus.ACCEPTED);
                trip.setAcceptedAt(LocalDateTime.now());
                trip = tripRepository.save(trip);

                log.info("[Trip] Trip {} ACCEPTED by driver {} ({})",
                                trip.getId(), driver.getId(), user.getEmail());

                // Thông báo cho Customer qua WebSocket
                matchingService.notifyCustomerDriverFound(trip);
                matchingService.broadcastTripStatus(trip, "Tài xế đang đến điểm đón");

                return toResponse(trip);
        }

        // -------------------------------------------------------------------------
        // ARRIVE: Driver đã đến điểm đón
        // -------------------------------------------------------------------------

        /**
         * Driver xác nhận đã đến điểm đón. Chỉ trip ACCEPTED mới arrive được.
         * Sau bước này, Customer nhận thông báo "Tài xế đã đến nơi".
         *
         * @param driverEmail email từ JWT principal
         * @param tripId      ID cuốc xe
         */
        @Transactional
        public TripResponse arriveAtPickup(String driverEmail, UUID tripId) {
                Trip trip = findTripAndValidateDriver(driverEmail, tripId, TripStatus.ACCEPTED);

                trip.setStatus(TripStatus.ARRIVED);
                trip.setArrivedAt(LocalDateTime.now());
                trip = tripRepository.save(trip);

                log.info("[Trip] Trip {} ARRIVED at pickup", trip.getId());
                matchingService.broadcastTripStatus(trip, "Tài xế đã đến điểm đón");

                return toResponse(trip);
        }

        // -------------------------------------------------------------------------
        // START: Driver bắt đầu chuyến đi (sau khi khách đã lên xe)
        // -------------------------------------------------------------------------

        /**
         * Driver bắt đầu chuyến đi. Chỉ trip ARRIVED mới start được.
         * (Đảm bảo driver phải bấm "Đã đến" trước, rồi mới "Bắt đầu chuyến".)
         *
         * @param driverEmail email từ JWT principal
         * @param tripId      ID cuốc xe
         */
        @Transactional
        public TripResponse startTrip(String driverEmail, UUID tripId) {
                Trip trip = findTripAndValidateDriver(driverEmail, tripId, TripStatus.ARRIVED);

                trip.setStatus(TripStatus.IN_PROGRESS);
                trip.setStartedAt(LocalDateTime.now());
                trip = tripRepository.save(trip);

                log.info("[Trip] Trip {} IN_PROGRESS", trip.getId());
                matchingService.broadcastTripStatus(trip, "Chuyến đi đã bắt đầu");

                return toResponse(trip);
        }

        // -------------------------------------------------------------------------
        // COMPLETE: Driver hoàn thành chuyến đi
        // -------------------------------------------------------------------------

        /**
         * Driver hoàn thành chuyến đi. Chỉ trip IN_PROGRESS mới complete được.
         *
         * @param driverEmail email từ JWT principal
         * @param tripId      ID cuốc xe
         */
        @Transactional
        public TripResponse completeTrip(String driverEmail, UUID tripId) {
                Trip trip = findTripAndValidateDriver(driverEmail, tripId, TripStatus.IN_PROGRESS);

                trip.setStatus(TripStatus.COMPLETED);
                trip.setCompletedAt(LocalDateTime.now());
                trip = tripRepository.save(trip);

                // Tăng totalTrips cho driver
                Driver driver = trip.getDriver();
                driver.setTotalTrips(driver.getTotalTrips() + 1);
                driverRepository.save(driver);

                log.info("[Trip] Trip {} COMPLETED | finalPrice={}", trip.getId(), trip.getFinalPrice());
                matchingService.broadcastTripStatus(trip, "Chuyến đi hoàn thành. Cảm ơn bạn!");

                return toResponse(trip);
        }

        // -------------------------------------------------------------------------
        // CANCEL: Customer hoặc Driver hủy cuốc
        // -------------------------------------------------------------------------

        /**
         * Hủy cuốc xe. Chỉ REQUESTED, ACCEPTED, IN_PROGRESS mới hủy được.
         * COMPLETED thì không thể hủy.
         *
         * @param userEmail email từ JWT principal (customer hoặc driver)
         * @param tripId    ID cuốc xe
         */
        @Transactional
        public TripResponse cancelTrip(String userEmail, UUID tripId) {
                Trip trip = tripRepository.findById(tripId)
                                .orElseThrow(() -> new AppException(ErrorCode.TRIP_NOT_FOUND));

                if (trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
                        throw new AppException(ErrorCode.INVALID_TRIP_STATUS,
                                        "Không thể hủy cuốc xe đã hoàn thành hoặc đã hủy.");
                }

                trip.setStatus(TripStatus.CANCELLED);
                trip = tripRepository.save(trip);

                log.info("[Trip] Trip {} CANCELLED by {}", trip.getId(), userEmail);
                matchingService.broadcastTripStatus(trip, "Cuốc xe đã bị hủy");

                return toResponse(trip);
        }

        // -------------------------------------------------------------------------
        // READ: Xem trip và lịch sử
        // -------------------------------------------------------------------------

        /**
         * Xem chi tiết 1 cuốc xe.
         */
        public TripResponse getTrip(UUID tripId) {
                Trip trip = tripRepository.findById(tripId)
                                .orElseThrow(() -> new AppException(ErrorCode.TRIP_NOT_FOUND));
                return toResponse(trip);
        }

        /**
         * Xem lịch sử cuốc xe của customer (mới nhất trước).
         */
        public List<TripResponse> getCustomerHistory(String customerEmail) {
                User customer = userRepository.findByEmail(customerEmail)
                                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
                return tripRepository.findByCustomerIdOrderByRequestedAtDesc(customer.getId())
                                .stream().map(this::toResponse).toList();
        }

        /**
         * Xem lịch sử cuốc xe của driver (mới nhất trước).
         */
        public List<TripResponse> getDriverHistory(String driverEmail) {
                User user = userRepository.findByEmail(driverEmail)
                                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
                Driver driver = driverRepository.findByUserIdWithUser(user.getId())
                                .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));
                return tripRepository.findByDriverIdOrderByRequestedAtDesc(driver.getId())
                                .stream().map(this::toResponse).toList();
        }

        // -------------------------------------------------------------------------
        // PRIVATE helpers
        // -------------------------------------------------------------------------

        /**
         * Tìm trip + validate rằng driver đang thao tác đúng là driver của trip đó,
         * và trip đang ở trạng thái cho phép.
         */
        private Trip findTripAndValidateDriver(String driverEmail, UUID tripId, TripStatus expectedStatus) {
                Trip trip = tripRepository.findById(tripId)
                                .orElseThrow(() -> new AppException(ErrorCode.TRIP_NOT_FOUND));

                if (trip.getStatus() != expectedStatus) {
                        throw new AppException(ErrorCode.INVALID_TRIP_STATUS,
                                        "Cuốc xe phải ở trạng thái " + expectedStatus + " để thực hiện thao tác này.");
                }

                User user = userRepository.findByEmail(driverEmail)
                                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
                Driver driver = driverRepository.findByUserIdWithUser(user.getId())
                                .orElseThrow(() -> new AppException(ErrorCode.DRIVER_NOT_FOUND));

                // Kiểm tra driver đang thao tác có đúng là driver của trip không
                if (trip.getDriver() == null || !trip.getDriver().getId().equals(driver.getId())) {
                        throw new AppException(ErrorCode.DRIVER_NOT_AUTHORIZED,
                                        "Bạn không phải là tài xế của cuốc xe này.");
                }

                return trip;
        }

        /**
         * Chuyển Trip entity sang TripResponse DTO.
         */
        private TripResponse toResponse(Trip trip) {
                return new TripResponse(
                                trip.getId().toString(),
                                trip.getCustomer().getId().toString(),
                                trip.getDriver() != null ? trip.getDriver().getId().toString() : null,
                                trip.getDriver() != null ? trip.getDriver().getUser().getFullName() : null,
                                trip.getPickupLat(),
                                trip.getPickupLng(),
                                trip.getPickupH3Index(),
                                trip.getDestLat(),
                                trip.getDestLng(),
                                trip.getDestAddress(),
                                trip.getStatus(),
                                trip.getBasePrice(),
                                trip.getSurgeMultiplier(),
                                trip.getFinalPrice(),
                                trip.getDistanceKm(),
                                trip.getDurationMinutes(),
                                trip.getRequestedAt(),
                                trip.getAcceptedAt(),
                                trip.getArrivedAt(),
                                trip.getStartedAt(),
                                trip.getCompletedAt());
        }
}
