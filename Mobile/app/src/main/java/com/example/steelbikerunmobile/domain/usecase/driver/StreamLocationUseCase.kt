package com.example.steelbikerunmobile.domain.usecase.driver

import com.example.steelbikerunmobile.domain.model.LocationHeartbeat
import com.example.steelbikerunmobile.domain.repository.DriverRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * Stream driver GPS location to backend via WebSocket.
 *
 * CRITICAL: Must call connectWebSocket() before the Flow starts, otherwise the
 * first heartbeat(s) will be dropped because the WebSocket is not yet connected.
 * See [connectWebSocket] — it must be called in the ViewModel before starting
 * this Flow.
 */
class StreamLocationUseCase @Inject constructor(
    private val driverRepository: DriverRepository
) {
    /**
     * Start streaming GPS heartbeats to the backend.
     * The Flow does NOT connect the WebSocket — caller must call [connectWebSocket] first.
     * This separation ensures the WebSocket handshake completes before any heartbeat is sent,
     * preventing the race condition where the first heartbeat is dropped.
     */
    operator fun invoke(): Flow<LocationHeartbeat> = driverRepository.observeLocation()
        .onEach { heartbeat -> driverRepository.sendLocationHeartbeat(heartbeat) }

    /**
     * Connect the WebSocket before starting the location Flow.
     * Must be called BEFORE [invoke] to avoid dropping the first heartbeat.
     */
    suspend fun connectWebSocket() = driverRepository.connectWebSocket()

    fun stop() {
        driverRepository.stopRealtime()
    }
}
