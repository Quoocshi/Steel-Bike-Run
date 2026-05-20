package org.example.steelbikerunbackend.module.websocket;

import org.example.steelbikerunbackend.module.driver.dto.LocationUpdateRequest;
import org.example.steelbikerunbackend.module.driver.dto.LocationUpdateResponse;
import org.example.steelbikerunbackend.module.driver.service.DriverLocationService;
import org.example.steelbikerunbackend.module.websocket.dto.DriverLocationUpdateResponse;
import org.example.steelbikerunbackend.module.websocket.dto.LocationHeartbeat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationWebSocketHandlerTest {

    @Mock
    private DriverLocationService driverLocationService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private StompHeaderAccessor headerAccessor;

    @InjectMocks
    private LocationWebSocketHandler handler;

    private static final String DRIVER_EMAIL = "driver@test.com";
    private static final String DRIVER_ID = UUID.randomUUID().toString();
    private static final double LAT = 10.7769;
    private static final double LNG = 106.7009;
    private static final String H3_INDEX = "891f1d4b2a3ffff";

    private LocationHeartbeat heartbeat;

    @BeforeEach
    void setUp() {
        heartbeat = LocationHeartbeat.builder()
                .latitude(LAT)
                .longitude(LNG)
                .heading(90.0f)
                .speed(30.5f)
                .build();
    }

    @Test
    @DisplayName("handleLocationHeartbeat: null Principal -> early return, no service call")
    void handleLocationHeartbeat_NullPrincipal_SkipsProcessing() {
        when(headerAccessor.getSessionId()).thenReturn("session-123");

        handler.handleLocationHeartbeat(heartbeat, null, headerAccessor);

        verifyNoInteractions(driverLocationService);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("handleLocationHeartbeat: push raw heartbeat to customer tracking topic")
    void handleLocationHeartbeat_PushesHeartbeatToCustomerTopic() {
        Principal principal = () -> DRIVER_EMAIL;
        LocationUpdateResponse serviceResponse = new LocationUpdateResponse(
                DRIVER_ID, LAT, LNG, H3_INDEX, Instant.now());
        when(driverLocationService.updateLocation(eq(DRIVER_EMAIL), any(LocationUpdateRequest.class)))
                .thenReturn(serviceResponse);

        handler.handleLocationHeartbeat(heartbeat, principal, headerAccessor);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/driver/" + DRIVER_ID + "/location"),
                eq(heartbeat));
    }

    @Test
    @DisplayName("handleLocationHeartbeat: push response with h3Index back to driver")
    void handleLocationHeartbeat_PushesH3ResponseToDriverTopic() {
        Principal principal = () -> DRIVER_EMAIL;
        Instant updatedAt = Instant.now();
        LocationUpdateResponse serviceResponse = new LocationUpdateResponse(
                DRIVER_ID, LAT, LNG, H3_INDEX, updatedAt);
        when(driverLocationService.updateLocation(eq(DRIVER_EMAIL), any(LocationUpdateRequest.class)))
                .thenReturn(serviceResponse);

        handler.handleLocationHeartbeat(heartbeat, principal, headerAccessor);

        ArgumentCaptor<DriverLocationUpdateResponse> payloadCaptor = ArgumentCaptor.forClass(DriverLocationUpdateResponse.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/driver/" + DRIVER_ID + "/location-update"),
                payloadCaptor.capture());

        DriverLocationUpdateResponse captured = payloadCaptor.getValue();
        assertThat(captured.getDriverId()).isEqualTo(DRIVER_ID);
        assertThat(captured.getLatitude()).isEqualTo(LAT);
        assertThat(captured.getLongitude()).isEqualTo(LNG);
        assertThat(captured.getH3Index()).isEqualTo(H3_INDEX);
    }

    @Test
    @DisplayName("handleLocationHeartbeat: service throws -> catches exception, no crash")
    void handleLocationHeartbeat_ServiceThrows_HandlesGracefully() {
        Principal principal = () -> DRIVER_EMAIL;
        when(driverLocationService.updateLocation(eq(DRIVER_EMAIL), any(LocationUpdateRequest.class)))
                .thenThrow(new RuntimeException("Redis unavailable"));

        // Should not throw
        handler.handleLocationHeartbeat(heartbeat, principal, headerAccessor);

        // No messages should be sent on failure
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("handleLocationHeartbeat: build LocationUpdateRequest with correct values from heartbeat")
    void handleLocationHeartbeat_BuildsCorrectLocationUpdateRequest() {
        Principal principal = () -> DRIVER_EMAIL;
        LocationUpdateResponse serviceResponse = new LocationUpdateResponse(
                DRIVER_ID, LAT, LNG, H3_INDEX, Instant.now());
        when(driverLocationService.updateLocation(eq(DRIVER_EMAIL), any(LocationUpdateRequest.class)))
                .thenReturn(serviceResponse);

        handler.handleLocationHeartbeat(heartbeat, principal, headerAccessor);

        ArgumentCaptor<LocationUpdateRequest> reqCaptor = ArgumentCaptor.forClass(LocationUpdateRequest.class);
        verify(driverLocationService).updateLocation(eq(DRIVER_EMAIL), reqCaptor.capture());

        LocationUpdateRequest captured = reqCaptor.getValue();
        assertThat(captured.latitude()).isEqualTo(LAT);
        assertThat(captured.longitude()).isEqualTo(LNG);
        assertThat(captured.heading()).isEqualTo(90.0f);
        assertThat(captured.speed()).isEqualTo(30.5f);
    }
}
