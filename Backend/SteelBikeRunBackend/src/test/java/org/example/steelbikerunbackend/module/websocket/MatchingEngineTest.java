package org.example.steelbikerunbackend.module.websocket;

import org.example.steelbikerunbackend.common.enums.TripStatus;
import org.example.steelbikerunbackend.module.driver.dto.NearbyDriverResponse;
import org.example.steelbikerunbackend.module.driver.service.DriverLocationService;
import org.example.steelbikerunbackend.module.trip.entity.Trip;
import org.example.steelbikerunbackend.module.trip.repository.TripRepository;
import org.example.steelbikerunbackend.module.user.entity.User;
import org.example.steelbikerunbackend.module.websocket.matching.MatchingEngine;
import org.example.steelbikerunbackend.module.websocket.matching.MatchingQueue;
import org.example.steelbikerunbackend.module.websocket.matching.TripMatchingState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingEngineTest {

    @Mock private MatchingQueue          matchingQueue;
    @Mock private TripRepository         tripRepository;
    @Mock private DriverLocationService  driverLocationService;
    @Mock private SimpMessagingTemplate  messagingTemplate;

    @InjectMocks private MatchingEngine matchingEngine;

    private static final String TRIP_ID     = UUID.randomUUID().toString();
    private static final String CUSTOMER_ID = UUID.randomUUID().toString();
    private static final String DRIVER_ID   = UUID.randomUUID().toString();

    private Trip requestedTrip;
    private TripMatchingState freshState;   // lastBroadcastAt = null (round chưa bắt đầu)
    private TripMatchingState waitingState; // lastBroadcastAt = now  (round đang chờ)
    private TripMatchingState expiredState; // lastBroadcastAt = now - 35s (round đã hết timeout 30s)

    @BeforeEach
    void setUp() {
        User customer = User.builder()
                .id(UUID.fromString(CUSTOMER_ID))
                .email("customer@test.com")
                .phone("0900000001")
                .fullName("Test Customer")
                .build();

        requestedTrip = Trip.builder()
                .id(UUID.fromString(TRIP_ID))
                .customer(customer)
                .pickupLat(10.7769)
                .pickupLng(106.7009)
                .pickupH3Index("891f1d4b2a3ffff")
                .destLat(10.8230)
                .destLng(106.6297)
                .destAddress("Sân bay Tân Sơn Nhất")
                .status(TripStatus.REQUESTED)
                .build();

        freshState = TripMatchingState.builder()
                .tripId(TRIP_ID)
                .customerId(CUSTOMER_ID)
                .pickupLat(10.7769)
                .pickupLng(106.7009)
                .createdAt(Instant.now())
                .round(0)
                .rejectedOrNotifiedDriverIds(new HashSet<>())
                .build();

        waitingState = TripMatchingState.builder()
                .tripId(TRIP_ID)
                .customerId(CUSTOMER_ID)
                .pickupLat(10.7769)
                .pickupLng(106.7009)
                .createdAt(Instant.now())
                .round(1)
                .lastBroadcastAt(Instant.now())  // round mới bắt đầu → chưa timeout
                .rejectedOrNotifiedDriverIds(new HashSet<>())
                .build();

        // lastBroadcastAt = 35 giây trước → đã vượt quá ROUND_TIMEOUT_SECONDS (30s)
        expiredState = TripMatchingState.builder()
                .tripId(TRIP_ID)
                .customerId(CUSTOMER_ID)
                .pickupLat(10.7769)
                .pickupLng(106.7009)
                .createdAt(Instant.now())
                .round(1)
                .lastBroadcastAt(Instant.now().minusSeconds(35))
                .rejectedOrNotifiedDriverIds(new HashSet<>())
                .build();
    }

    // -------------------------------------------------------------------------
    // tick() — queue rỗng
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("tick: Queue rỗng → không làm gì")
    void tick_EmptyQueue_DoesNothing() {
        when(matchingQueue.getAllTripIds()).thenReturn(List.of());

        matchingEngine.tick();

        verify(tripRepository, never()).findByIdWithCustomer(any());
    }

    // -------------------------------------------------------------------------
    // State đã hết TTL (getState trả empty)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("tick: State Redis hết TTL → remove trip khỏi queue")
    void tick_StateExpired_RemovesFromQueue() {
        when(matchingQueue.getAllTripIds()).thenReturn(List.of(TRIP_ID));
        when(matchingQueue.getState(TRIP_ID)).thenReturn(Optional.empty());

        matchingEngine.tick();

        verify(matchingQueue).remove(TRIP_ID);
        verify(tripRepository, never()).findByIdWithCustomer(any());
    }

    // -------------------------------------------------------------------------
    // Trip không còn REQUESTED (đã ACCEPTED/CANCELLED)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("tick: Trip đã ACCEPTED → remove khỏi queue")
    void tick_TripAlreadyAccepted_RemovesFromQueue() {
        requestedTrip.setStatus(TripStatus.ACCEPTED);
        when(matchingQueue.getAllTripIds()).thenReturn(List.of(TRIP_ID));
        when(matchingQueue.getState(TRIP_ID)).thenReturn(Optional.of(freshState));
        when(tripRepository.findByIdWithCustomer(UUID.fromString(TRIP_ID)))
                .thenReturn(Optional.of(requestedTrip));

        matchingEngine.tick();

        verify(matchingQueue).remove(TRIP_ID);
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    // -------------------------------------------------------------------------
    // Round đang chờ (lastBroadcastAt = now) → skip
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("tick: Round hiện tại chưa timeout → skip, không broadcast")
    void tick_RoundStillWaiting_SkipsProcessing() {
        when(matchingQueue.getAllTripIds()).thenReturn(List.of(TRIP_ID));
        when(matchingQueue.getState(TRIP_ID)).thenReturn(Optional.of(waitingState));
        when(tripRepository.findByIdWithCustomer(UUID.fromString(TRIP_ID)))
                .thenReturn(Optional.of(requestedTrip));

        matchingEngine.tick();

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        verify(matchingQueue, never()).updateState(any());
    }

    // -------------------------------------------------------------------------
    // Round 1 — không có driver → quick retry (5s) thay vì full 30s
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("tick: Round 1, không có driver → dùng quick retry 5s, notify customer")
    void tick_Round1_NoDrivers_QuickRetry() {
        when(matchingQueue.getAllTripIds()).thenReturn(List.of(TRIP_ID));
        when(matchingQueue.getState(TRIP_ID)).thenReturn(Optional.of(freshState));
        when(tripRepository.findByIdWithCustomer(UUID.fromString(TRIP_ID)))
                .thenReturn(Optional.of(requestedTrip));
        when(driverLocationService.findNearbyDrivers(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(List.of());

        matchingEngine.tick();

        // State phải được update (lastBroadcastAt đặt thành quá khứ cho quick retry)
        ArgumentCaptor<TripMatchingState> stateCaptor = ArgumentCaptor.forClass(TripMatchingState.class);
        verify(matchingQueue).updateState(stateCaptor.capture());
        TripMatchingState saved = stateCaptor.getValue();

        // lastBroadcastAt phải ở TRONG QUÁ KHỨ (= now - 5s gần đúng)
        // tức là < now để tick tiếp theo sau 5s sẽ thấy đã hết timeout
        assertThat(saved.getLastBroadcastAt()).isBefore(Instant.now());
        assertThat(saved.getRound()).isEqualTo(1);

        // Notify customer "Đang tìm..."
        verify(messagingTemplate, atLeastOnce()).convertAndSend(
                eq("/topic/trip/" + CUSTOMER_ID), any(Object.class));
    }

    // -------------------------------------------------------------------------
    // Round 2+ — không còn driver mới → retry với full timeout
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("tick: Round 2+, không có driver mới → đợi đủ timeout rồi retry")
    void tick_SubsequentRound_NoFreshDrivers_WaitsFullTimeout() {
        when(matchingQueue.getAllTripIds()).thenReturn(List.of(TRIP_ID));
        when(matchingQueue.getState(TRIP_ID)).thenReturn(Optional.of(expiredState));
        when(tripRepository.findByIdWithCustomer(UUID.fromString(TRIP_ID)))
                .thenReturn(Optional.of(requestedTrip));
        when(driverLocationService.findNearbyDrivers(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(List.of());

        matchingEngine.tick();

        ArgumentCaptor<TripMatchingState> stateCaptor = ArgumentCaptor.forClass(TripMatchingState.class);
        verify(matchingQueue).updateState(stateCaptor.capture());
        TripMatchingState saved = stateCaptor.getValue();

        // lastBroadcastAt phải là NOW (không phải quá khứ xa)
        assertThat(saved.getLastBroadcastAt()).isBetween(
                Instant.now().minusSeconds(3), Instant.now().plusSeconds(1));
    }

    // -------------------------------------------------------------------------
    // Broadcast thành công — có driver trong round
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("tick: Tìm được driver → broadcast đến driver qua WebSocket")
    void tick_DriverFound_BroadcastsToDriver() {
        NearbyDriverResponse nearbyDriver = new NearbyDriverResponse(
                DRIVER_ID, "Nguyen Van A", "51G-111.11",
                "Honda Wave", "Blue", 4.8f,
                10.778, 106.701, "891f1d4b2a3ffff",
                0.5, null, null);

        when(matchingQueue.getAllTripIds()).thenReturn(List.of(TRIP_ID));
        when(matchingQueue.getState(TRIP_ID)).thenReturn(Optional.of(freshState));
        when(tripRepository.findByIdWithCustomer(UUID.fromString(TRIP_ID)))
                .thenReturn(Optional.of(requestedTrip));
        when(driverLocationService.findNearbyDrivers(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(List.of(nearbyDriver));

        matchingEngine.tick();

        // Broadcast đến topic của driver
        verify(messagingTemplate).convertAndSend(
                eq("/topic/driver/" + DRIVER_ID), any(Object.class));

        // State update: driver đã vào danh sách notified
        ArgumentCaptor<TripMatchingState> stateCaptor = ArgumentCaptor.forClass(TripMatchingState.class);
        verify(matchingQueue).updateState(stateCaptor.capture());
        assertThat(stateCaptor.getValue().getRejectedOrNotifiedDriverIds()).contains(DRIVER_ID);
    }

    // -------------------------------------------------------------------------
    // Global timeout → CANCEL trip
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("tick: Global timeout 5 phút → CANCEL trip + notify customer")
    void tick_GlobalTimeout_CancelsTrip() {
        TripMatchingState timedOutState = TripMatchingState.builder()
                .tripId(TRIP_ID)
                .customerId(CUSTOMER_ID)
                .pickupLat(10.7769)
                .pickupLng(106.7009)
                .createdAt(Instant.now().minusSeconds(310)) // 5 phút 10 giây trước
                .round(15)
                .lastBroadcastAt(Instant.now().minusSeconds(35))
                .rejectedOrNotifiedDriverIds(new HashSet<>())
                .build();

        when(matchingQueue.getAllTripIds()).thenReturn(List.of(TRIP_ID));
        when(matchingQueue.getState(TRIP_ID)).thenReturn(Optional.of(timedOutState));
        when(tripRepository.findByIdWithCustomer(UUID.fromString(TRIP_ID)))
                .thenReturn(Optional.of(requestedTrip));
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

        matchingEngine.tick();

        // Trip phải được lưu với status CANCELLED
        ArgumentCaptor<Trip> tripCaptor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(tripCaptor.capture());
        assertThat(tripCaptor.getValue().getStatus()).isEqualTo(TripStatus.CANCELLED);

        // Remove khỏi queue
        verify(matchingQueue).remove(TRIP_ID);

        // Notify customer thất bại
        verify(messagingTemplate).convertAndSend(
                eq("/topic/trip/" + CUSTOMER_ID), any(Object.class));
    }
}
