package com.example.steelbikerunmobile.domain.usecase.trip

import com.example.steelbikerunmobile.data.remote.websocket.StompWebSocketManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

/**
 * ObserveDriverTripRequestsUseCase — lắng nghe cuốc xe mới qua WebSocket cho Driver.
 *
 * Driver subscribe vào /topic/driver/{driverId} để nhận:
 * - TripRequestMessage: khi có khách đặt xe gần vị trí driver
 * - TripStatusMessage: khi trạng thái cuốc thay đổi (sau khi accept)
 */
class ObserveDriverTripRequestsUseCase @Inject constructor(
    private val stompManager: StompWebSocketManager,
    private val gson: Gson
) {
    private var subscriptionId: String? = null

    /**
     * Kết nối WebSocket và subscribe vào kênh trip requests.
     * @param driverId ID tài xế để subscribe kênh đúng
     */
    suspend fun subscribe(driverId: String) {
        stompManager.connect()
        subscriptionId = stompManager.subscribe("/topic/driver/$driverId")
    }

    /**
     * Kết nối WebSocket và đợi cho đến khi CONNECTED trước khi subscribe.
     * Dùng khi cần đảm bảo WebSocket đã sẵn sàng trước khi thực hiện subscribe.
     *
     * @param timeoutMillis thời gian tối đa chờ (mặc định 10 giây)
     * @return true nếu đã connected, false nếu timeout
     */
    suspend fun connectAndWaitForConnection(timeoutMillis: Long = 10_000L): Boolean {
        stompManager.connect()
        return stompManager.waitForConnection(timeoutMillis)
    }

    /**
     * Flow nhận TripRequestMessage từ server.
     * Phát ra khi có cuốc xe mới gần vị trí driver.
     */
    fun tripRequests(): Flow<TripRequestData> {
        return stompManager.incomingMessages
            .filter { it.destination.startsWith("/topic/driver/") }
            .mapNotNull { msg ->
                try {
                    val json = gson.fromJson(msg.body, JsonObject::class.java)
                    if (json.has("tripId") && json.has("pickupLat")) {
                        TripRequestData(
                            tripId = json.get("tripId")?.asString ?: "",
                            customerId = json.get("customerId")?.asString ?: "",
                            customerName = json.get("customerName")?.asString ?: "",
                            customerPhone = json.get("customerPhone")?.asString ?: "",
                            pickupLat = json.get("pickupLat")?.asDouble ?: 0.0,
                            pickupLng = json.get("pickupLng")?.asDouble ?: 0.0,
                            destLat = json.get("destLat")?.asDouble ?: 0.0,
                            destLng = json.get("destLng")?.asDouble ?: 0.0,
                            destDistanceKm = json.get("destDistanceKm")?.asDouble ?: 0.0,
                            destAddress = json.get("destAddress")?.asString ?: "",
                            finalPrice = json.get("finalPrice")?.asLong ?: 0L,
                            surgeMultiplier = json.get("surgeMultiplier")?.asDouble ?: 1.0,
                            distanceToPickupKm = json.get("distanceToPickupKm")?.asDouble ?: 0.0,
                            timeoutSeconds = json.get("timeoutSeconds")?.asInt ?: 30,
                        )
                    } else null
                } catch (_: Exception) { null }
            }
    }

    fun unsubscribe() {
        subscriptionId?.let { stompManager.unsubscribe(it) }
        subscriptionId = null
    }
}

data class TripRequestData(
    val tripId: String,
    val customerId: String,
    val customerName: String,
    val customerPhone: String,
    val pickupLat: Double,
    val pickupLng: Double,
    val destLat: Double,
    val destLng: Double,
    val destDistanceKm: Double,
    val destAddress: String,
    val finalPrice: Long,
    val surgeMultiplier: Double,
    val distanceToPickupKm: Double,
    val timeoutSeconds: Int,
)
