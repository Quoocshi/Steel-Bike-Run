package com.example.steelbikerunmobile.domain.usecase.trip

import com.example.steelbikerunmobile.data.remote.websocket.StompMessage
import com.example.steelbikerunmobile.data.remote.websocket.StompWebSocketManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mapbox.mapboxsdk.geometry.LatLng
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

/**
 * ObserveTripUpdatesUseCase — lắng nghe cập nhật realtime cho cuốc xe qua WebSocket.
 *
 * Customer subscribe vào /topic/trip/{customerId} để nhận:
 * - DriverFoundMessage: khi tài xế accept cuốc
 * - TripStatusMessage: khi trạng thái cuốc thay đổi (ACCEPTED, IN_PROGRESS, COMPLETED, CANCELLED)
 */
class ObserveTripUpdatesUseCase @Inject constructor(
    private val stompManager: StompWebSocketManager,
    private val gson: Gson
) {
    private var subscriptionId: String? = null

    /**
     * Kết nối WebSocket và subscribe vào kênh trip updates.
     * @param customerId ID khách hàng để subscribe kênh đúng
     */
    suspend fun subscribe(customerId: String) {
        stompManager.connect()
        subscriptionId = stompManager.subscribe("/topic/trip/$customerId")
    }

    /**
     * Flow nhận DriverFoundMessage từ server.
     * Phát ra khi tài xế accept cuốc xe.
     */
    fun driverFoundMessages(): Flow<DriverFoundData> {
        return stompManager.incomingMessages
            .filter { it.destination.startsWith("/topic/trip/") }
            .mapNotNull { msg ->
                try {
                    val json = gson.fromJson(msg.body, JsonObject::class.java)
                    // DriverFoundMessage có field driverId + driverName
                    if (json.has("driverId") && json.has("driverName")) {
                        DriverFoundData(
                            tripId = json.get("tripId")?.asString ?: "",
                            driverId = json.get("driverId")?.asString ?: "",
                            driverName = json.get("driverName")?.asString ?: "",
                            vehiclePlate = json.get("vehiclePlate")?.asString ?: "",
                            vehicleModel = json.get("vehicleModel")?.asString ?: "",
                            vehicleColor = json.get("vehicleColor")?.asString ?: "",
                            driverRating = json.get("driverRating")?.asFloat ?: 0f,
                            etaMinutes = json.get("etaMinutes")?.asInt ?: 5,
                        )
                    } else null
                } catch (_: Exception) { null }
            }
    }

    /**
     * Flow nhận TripStatusMessage từ server.
     * Phát ra khi trạng thái cuốc xe thay đổi.
     */
    fun tripStatusMessages(): Flow<TripStatusData> {
        return stompManager.incomingMessages
            .filter { it.destination.startsWith("/topic/trip/") }
            .mapNotNull { msg ->
                try {
                    val json = gson.fromJson(msg.body, JsonObject::class.java)
                    // TripStatusMessage có field status + message
                    if (json.has("status") && json.has("message") && !json.has("driverId")) {
                        TripStatusData(
                            tripId = json.get("tripId")?.asString ?: "",
                            status = json.get("status")?.asString ?: "",
                            message = json.get("message")?.asString ?: "",
                        )
                    } else null
                } catch (_: Exception) { null }
            }
    }

    fun unsubscribe() {
        subscriptionId?.let { stompManager.unsubscribe(it) }
        subscriptionId = null
    }

    /**
     * Flow nhận location cập nhật từ tài xế
     */
    fun driverLocationMessages(driverId: String): Flow<LatLng> {
        // Tự động subscribe nếu chưa
        stompManager.subscribe("/topic/driver/$driverId/location")
        
        return stompManager.incomingMessages
            .filter { it.destination == "/topic/driver/$driverId/location" }
            .mapNotNull { msg ->
                try {
                    val json = gson.fromJson(msg.body, JsonObject::class.java)
                    if (json.has("latitude") && json.has("longitude")) {
                        LatLng(
                            json.get("latitude").asDouble,
                            json.get("longitude").asDouble
                        )
                    } else null
                } catch (_: Exception) { null }
            }
    }
}

// Data classes cho domain layer
data class DriverFoundData(
    val tripId: String,
    val driverId: String,
    val driverName: String,
    val vehiclePlate: String,
    val vehicleModel: String,
    val vehicleColor: String,
    val driverRating: Float,
    val etaMinutes: Int,
)

data class TripStatusData(
    val tripId: String,
    val status: String,   // REQUESTED, ACCEPTED, IN_PROGRESS, COMPLETED, CANCELLED
    val message: String,
)
