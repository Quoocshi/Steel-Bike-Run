package org.example.steelbikerunbackend.module.websocket.matching;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.steelbikerunbackend.common.enums.TripStatus;
import org.example.steelbikerunbackend.module.driver.dto.NearbyDriverResponse;
import org.example.steelbikerunbackend.module.driver.service.DriverLocationService;
import org.example.steelbikerunbackend.module.trip.entity.Trip;
import org.example.steelbikerunbackend.module.trip.repository.TripRepository;
import org.example.steelbikerunbackend.module.websocket.dto.TripRequestMessage;
import org.example.steelbikerunbackend.module.websocket.dto.TripStatusMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * MatchingEngine — vòng lặp nền tự động gán cuốc xe cho tài xế.
 *
 * <h3>Cơ chế hoạt động:</h3>
 * <ul>
 *   <li>Mỗi 5 giây, scan toàn bộ trip đang trong {@link MatchingQueue}.</li>
 *   <li>Với mỗi trip, kiểm tra:
 *     <ol>
 *       <li><b>Đã có driver accept?</b> → remove khỏi queue (driver gọi /accept trực tiếp).</li>
 *       <li><b>Quá global timeout (5 phút)?</b> → CANCEL trip, notify customer.</li>
 *       <li><b>Round hiện tại chưa hết chờ (20s)?</b> → skip, chờ tiếp.</li>
 *       <li><b>Round hết chờ, chưa có driver nhận?</b> → tìm driver mới (loại bỏ đã notified),
 *           broadcast round kế tiếp. Nếu không còn driver nào → thử lại sau 5s.</li>
 *     </ol>
 *   </li>
 * </ul>
 *
 * <h3>Config:</h3>
 * <ul>
 *   <li>{@code ROUND_TIMEOUT_SECONDS = 20} — thời gian chờ driver phản hồi mỗi round.</li>
 *   <li>{@code GLOBAL_TIMEOUT_MINUTES = 5} — tổng thời gian tìm trước khi CANCEL.</li>
 *   <li>{@code DRIVERS_PER_ROUND = 3} — số driver broadcast mỗi round.</li>
 *   <li>{@code SEARCH_KRING = 13} — bán kính tìm (~4km).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingEngine {

    // ── Config ────────────────────────────────────────────────────────────────
    private static final int    ROUND_TIMEOUT_SECONDS  = 20;
    private static final int    GLOBAL_TIMEOUT_MINUTES = 5;
    private static final int    DRIVERS_PER_ROUND      = 3;
    private static final int    SEARCH_KRING           = 13;

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final MatchingQueue           matchingQueue;
    private final TripRepository          tripRepository;
    private final DriverLocationService   driverLocationService;
    private final SimpMessagingTemplate   messagingTemplate;

    // ── Scheduled task ────────────────────────────────────────────────────────

    /**
     * Poll 5 giây / lần. Sử dụng fixedDelay để tránh overlap nếu 1 cycle chạy lâu.
     */
    @Scheduled(fixedDelay = 5_000)
    public void tick() {
        List<String> tripIds = matchingQueue.getAllTripIds();
        if (tripIds.isEmpty()) return;

        log.debug("[MatchingEngine] tick — {} trip(s) in queue", tripIds.size());

        for (String tripId : tripIds) {
            try {
                processTripMatching(tripId);
            } catch (Exception e) {
                log.error("[MatchingEngine] Error processing trip {}: {}", tripId, e.getMessage(), e);
            }
        }
    }

    // ── Core logic ────────────────────────────────────────────────────────────

    private void processTripMatching(String tripId) {
        // 1. Đọc TripMatchingState từ Redis
        Optional<TripMatchingState> stateOpt = matchingQueue.getState(tripId);
        if (stateOpt.isEmpty()) {
            // State bị hết TTL (hiếm gặp) → dọn dẹp queue
            matchingQueue.remove(tripId);
            return;
        }
        TripMatchingState state = stateOpt.get();

        // 2. Đọc Trip từ DB với JOIN FETCH customer — tránh LazyInitializationException
        //    vì @Scheduled thread không có Hibernate session sau khi transaction đóng.
        Optional<Trip> tripOpt = tripRepository.findByIdWithCustomer(UUID.fromString(tripId));
        if (tripOpt.isEmpty()) {
            log.warn("[MatchingEngine] Trip {} không tồn tại trong DB → remove queue", tripId);
            matchingQueue.remove(tripId);
            return;
        }
        Trip trip = tripOpt.get();

        // 3. Nếu trip đã không còn REQUESTED (driver đã accept hoặc bị cancel) → dọn dẹp
        if (trip.getStatus() != TripStatus.REQUESTED) {
            log.info("[MatchingEngine] Trip {} status={} → remove from queue", tripId, trip.getStatus());
            matchingQueue.remove(tripId);
            return;
        }

        // 4. Kiểm tra global timeout
        long elapsedMinutes = Duration.between(state.getCreatedAt(), Instant.now()).toMinutes();
        if (elapsedMinutes >= GLOBAL_TIMEOUT_MINUTES) {
            log.warn("[MatchingEngine] Trip {} timed out after {}min → CANCEL", tripId, elapsedMinutes);
            cancelTrip(trip, state);
            return;
        }

        // 5. Kiểm tra round timeout — chỉ broadcast round mới sau ROUND_TIMEOUT_SECONDS
        if (state.getLastBroadcastAt() != null) {
            long secsSinceBroadcast = Duration.between(state.getLastBroadcastAt(), Instant.now()).getSeconds();
            if (secsSinceBroadcast < ROUND_TIMEOUT_SECONDS) {
                // Round hiện tại chưa hết chờ → skip
                log.debug("[MatchingEngine] Trip {} round {} — {}s / {}s elapsed, waiting...",
                        tripId, state.getRound(), secsSinceBroadcast, ROUND_TIMEOUT_SECONDS);
                return;
            }
        }

        // 6. Broadcast round mới — tìm tài xế gần nhất chưa được notified
        broadcastNextRound(trip, state);
    }

    /**
     * Tìm tài xế khả dụng (loại bỏ đã notified/từ chối) và broadcast round kế.
     */
    private void broadcastNextRound(Trip trip, TripMatchingState state) {
        // Lấy top (DRIVERS_PER_ROUND + đã_notified) để có đủ driver mới sau khi lọc
        int fetchLimit = DRIVERS_PER_ROUND + state.getRejectedOrNotifiedDriverIds().size() + 5;
        List<NearbyDriverResponse> allNearby = driverLocationService.findNearbyDrivers(
                state.getPickupLat(), state.getPickupLng(), SEARCH_KRING, fetchLimit);

        // Lọc ra driver chưa từng được thông báo
        List<NearbyDriverResponse> freshDrivers = allNearby.stream()
                .filter(d -> !state.getRejectedOrNotifiedDriverIds().contains(d.driverId()))
                .limit(DRIVERS_PER_ROUND)
                .toList();

        int nextRound = state.getRound() + 1;

        if (freshDrivers.isEmpty()) {
            if (state.getLastBroadcastAt() == null) {
                // Round 1 nhưng không có ai gần — vẫn cập nhật để tiếp tục retry
                log.warn("[MatchingEngine] Trip {} round 1 — không có driver nào gần. Retry sau {}s.",
                        trip.getId(), ROUND_TIMEOUT_SECONDS);
                state.setLastBroadcastAt(Instant.now());
                state.setRound(nextRound);
                matchingQueue.updateState(state);

                // Báo customer biết đang tìm
                notifyCustomerSearching(state, "Đang tìm tài xế trong khu vực...");
            } else {
                log.warn("[MatchingEngine] Trip {} round {} — không còn driver mới nào. Retry sau {}s.",
                        trip.getId(), nextRound, ROUND_TIMEOUT_SECONDS);
                state.setLastBroadcastAt(Instant.now());
                state.setRound(nextRound);
                matchingQueue.updateState(state);
            }
            return;
        }

        // Broadcast đến từng driver trong round này
        for (NearbyDriverResponse driver : freshDrivers) {
            TripRequestMessage message = TripRequestMessage.builder()
                    .tripId(trip.getId().toString())
                    .customerId(trip.getCustomer().getId().toString())
                    .customerName(trip.getCustomer().getFullName())
                    .customerPhone(trip.getCustomer().getPhone())
                    .pickupLat(trip.getPickupLat())
                    .pickupLng(trip.getPickupLng())
                    .pickupH3Index(trip.getPickupH3Index())
                    .destLat(trip.getDestLat())
                    .destLng(trip.getDestLng())
                    .destAddress(trip.getDestAddress())
                    .finalPrice(trip.getFinalPrice())
                    .surgeMultiplier(trip.getSurgeMultiplier())
                    .distanceToPickupKm(driver.distanceKm())
                    .timeoutSeconds(ROUND_TIMEOUT_SECONDS)
                    .build();

            String destination = "/topic/driver/" + driver.driverId();
            messagingTemplate.convertAndSend(destination, message);

            state.getRejectedOrNotifiedDriverIds().add(driver.driverId());

            log.info("[MatchingEngine] Trip {} round {} → Driver {} (dist={}km)",
                    trip.getId(), nextRound, driver.driverId(),
                    String.format("%.2f", driver.distanceKm()));
        }

        // Cập nhật state
        state.setRound(nextRound);
        state.setLastBroadcastAt(Instant.now());
        matchingQueue.updateState(state);

        // Thông báo customer biết đang tìm (round > 1 → "đang tìm thêm")
        if (nextRound > 1) {
            notifyCustomerSearching(state, "Đang tìm tài xế phù hợp, vui lòng chờ...");
        }

        log.info("[MatchingEngine] Trip {} round {} — broadcast to {} driver(s)",
                trip.getId(), nextRound, freshDrivers.size());
    }

    /**
     * Hủy trip sau khi quá global timeout.
     */
    private void cancelTrip(Trip trip, TripMatchingState state) {
        trip.setStatus(TripStatus.CANCELLED);
        tripRepository.save(trip);
        matchingQueue.remove(trip.getId().toString());

        // Thông báo khách không tìm được tài xế
        TripStatusMessage message = TripStatusMessage.builder()
                .tripId(trip.getId().toString())
                .status(TripStatus.CANCELLED.name())
                .message("Không tìm được tài xế sau 5 phút. Vui lòng thử lại.")
                .timestamp(Instant.now().toString())
                .build();

        String destination = "/topic/trip/" + state.getCustomerId();
        messagingTemplate.convertAndSend(destination, message);

        log.warn("[MatchingEngine] Trip {} CANCELLED — no driver found within {}min",
                trip.getId(), GLOBAL_TIMEOUT_MINUTES);
    }

    /**
     * Gửi thông báo cho customer biết đang trong trạng thái tìm kiếm.
     */
    private void notifyCustomerSearching(TripMatchingState state, String message) {
        TripStatusMessage statusMsg = TripStatusMessage.builder()
                .tripId(state.getTripId())
                .status("SEARCHING")
                .message(message)
                .timestamp(Instant.now().toString())
                .build();
        String destination = "/topic/trip/" + state.getCustomerId();
        messagingTemplate.convertAndSend(destination, statusMsg);
    }
}
