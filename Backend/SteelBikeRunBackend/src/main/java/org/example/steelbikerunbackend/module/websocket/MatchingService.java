package org.example.steelbikerunbackend.module.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.steelbikerunbackend.module.driver.dto.NearbyDriverResponse;
import org.example.steelbikerunbackend.module.driver.service.DriverLocationService;
import org.example.steelbikerunbackend.module.trip.entity.Trip;
import org.example.steelbikerunbackend.module.websocket.dto.DriverFoundMessage;
import org.example.steelbikerunbackend.module.websocket.dto.TripRequestMessage;
import org.example.steelbikerunbackend.module.websocket.dto.TripStatusMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MatchingService — logic khớp lệnh giữa Customer và Driver qua WebSocket.
 *
 * <h3>Luồng hoạt động:</h3>
 * <pre>
 * 1. Customer tạo trip (REST API POST /api/v1/trip)
 * 2. TripService gọi MatchingService.broadcastToNearbyDrivers()
 * 3. MatchingService dùng DriverLocationService.findNearbyDrivers() để lấy top 3
 * 4. Gửi TripRequestMessage qua /topic/driver/{driverId} cho từng driver
 * 5. Driver bấm nhận -> REST API PUT /api/v1/trip/{id}/accept
 * 6. TripService gọi MatchingService.notifyCustomerDriverFound()
 * 7. Gửi DriverFoundMessage qua /topic/trip/{customerId}
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    // Thời gian tối đa driver phải phản hồi (giây)
    private static final int DRIVER_TIMEOUT_SECONDS = 30;

    // Tối đa số driver được broadcast (top gần nhất)
    private static final int MAX_BROADCAST_DRIVERS = 3;

    // k-ring radius cho tìm driver (k=2 -> 19 ô H3 -> ~350m)
    private static final int SEARCH_KRING = 2;

    private final SimpMessagingTemplate messagingTemplate;
    private final DriverLocationService driverLocationService;

    /**
     * Broadcast cuốc xe mới đến top N tài xế gần nhất.
     *
     * @param trip cuốc xe vừa tạo (status=REQUESTED)
     * @return số lượng driver đã nhận được thông báo
     */
    public int broadcastToNearbyDrivers(Trip trip) {
        // Tìm top 3 tài xế gần điểm đón nhất từ Redis
        List<NearbyDriverResponse> nearbyDrivers = driverLocationService.findNearbyDrivers(
                trip.getPickupLat(), trip.getPickupLng(),
                SEARCH_KRING, MAX_BROADCAST_DRIVERS
        );

        if (nearbyDrivers.isEmpty()) {
            log.warn("[Matching] Trip {} - Không tìm thấy driver nào gần điểm đón [{}, {}]",
                    trip.getId(), trip.getPickupLat(), trip.getPickupLng());
            return 0;
        }

        // Build message cho mỗi driver (mỗi driver có distanceToPickup khác nhau)
        for (NearbyDriverResponse driver : nearbyDrivers) {
            TripRequestMessage message = TripRequestMessage.builder()
                    .tripId(trip.getId().toString())
                    .customerId(trip.getCustomer().getId().toString())
                    .customerName(trip.getCustomer().getFullName())
                    .pickupLat(trip.getPickupLat())
                    .pickupLng(trip.getPickupLng())
                    .pickupH3Index(trip.getPickupH3Index())
                    .destLat(trip.getDestLat())
                    .destLng(trip.getDestLng())
                    .destAddress(trip.getDestAddress())
                    .finalPrice(trip.getFinalPrice())
                    .surgeMultiplier(trip.getSurgeMultiplier())
                    .distanceToPickupKm(driver.distanceKm())
                    .timeoutSeconds(DRIVER_TIMEOUT_SECONDS)
                    .build();

            // Gửi đến kênh riêng của từng driver
            String destination = "/topic/driver/" + driver.driverId();
            messagingTemplate.convertAndSend(destination, message);

            log.info("[Matching] Trip {} -> broadcast to Driver {} (dist={}km)",
                    trip.getId(), driver.driverId(),
                    String.format("%.2f", driver.distanceKm()));
        }

        log.info("[Matching] Trip {} - Broadcast thành công đến {} driver",
                trip.getId(), nearbyDrivers.size());
        return nearbyDrivers.size();
    }

    /**
     * Thông báo cho Customer khi Driver accept cuốc xe.
     *
     * @param trip cuốc xe đã được accept (có driver info)
     */
    public void notifyCustomerDriverFound(Trip trip) {
        DriverFoundMessage message = DriverFoundMessage.builder()
                .tripId(trip.getId().toString())
                .driverId(trip.getDriver().getId().toString())
                .driverName(trip.getDriver().getUser().getFullName())
                .vehiclePlate(trip.getDriver().getVehiclePlate())
                .vehicleModel(trip.getDriver().getVehicleModel())
                .vehicleColor(trip.getDriver().getVehicleColor())
                .driverRating(trip.getDriver().getRating())
                // TODO: lấy vị trí driver từ Redis để tính ETA chính xác hơn
                .etaMinutes(5)
                .build();

        String destination = "/topic/trip/" + trip.getCustomer().getId().toString();
        messagingTemplate.convertAndSend(destination, message);

        log.info("[Matching] Trip {} - Notify customer {} : driver {} found",
                trip.getId(), trip.getCustomer().getId(), trip.getDriver().getId());
    }

    /**
     * Broadcast cập nhật trạng thái cuốc xe đến cả Customer và Driver.
     *
     * @param trip        cuốc xe
     * @param statusMsg   thông điệp mô tả trạng thái
     */
    public void broadcastTripStatus(Trip trip, String statusMsg) {
        TripStatusMessage message = TripStatusMessage.builder()
                .tripId(trip.getId().toString())
                .status(trip.getStatus().name())
                .message(statusMsg)
                .timestamp(LocalDateTime.now().toString())
                .build();

        // Gửi cho Customer
        String customerDest = "/topic/trip/" + trip.getCustomer().getId().toString();
        messagingTemplate.convertAndSend(customerDest, message);

        // Gửi cho Driver (nếu đã có)
        if (trip.getDriver() != null) {
            String driverDest = "/topic/driver/" + trip.getDriver().getId().toString();
            messagingTemplate.convertAndSend(driverDest, message);
        }

        log.debug("[Matching] Trip {} status broadcast: {} - {}",
                trip.getId(), trip.getStatus(), statusMsg);
    }
}
