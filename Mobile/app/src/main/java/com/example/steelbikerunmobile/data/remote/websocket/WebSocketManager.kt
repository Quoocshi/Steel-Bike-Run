package com.example.steelbikerunmobile.data.remote.websocket

import com.example.steelbikerunmobile.BuildConfig
import com.example.steelbikerunmobile.data.local.datastore.AuthPreferencesDataStore
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebSocket client dùng cho các tính năng realtime cần STOMP broker:
 *   - Nhận thông báo trip mới (Matching Engine → Driver)
 *   - (Tương lai) Broadcast vị trí driver → Customer
 *
 * Hiện tại backend chưa triển khai WebSocket Matching Engine (placeholder).
 * Class này được giữ sẵn sàng để kích hoạt khi backend hoàn thiện.
 *
 * KHÔNG dùng WebSocket để gửi location heartbeat — heartbeat đi qua REST
 * (POST /api/v1/driver/location) vì backend đã implement endpoint đó.
 */
@Singleton
class WebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val dataStore: AuthPreferencesDataStore
) {
    private var webSocket: WebSocket? = null

    /**
     * Kết nối WebSocket với JWT token từ DataStore.
     * Phải gọi từ coroutine context (suspend) để tránh blocking Main thread.
     */
    suspend fun connect() {
        if (webSocket != null) return
        val token = dataStore.tokenFlow.first()
        val requestBuilder = Request.Builder().url(BuildConfig.WS_URL)
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        webSocket = okHttpClient.newWebSocket(
            requestBuilder.build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("CONNECT\naccept-version:1.2\nheart-beat:10000,10000\n\n\u0000")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    this@WebSocketManager.webSocket = null
                }
            }
        )
    }

    /**
     * Gửi frame STOMP đến /app/driver.location.
     * Chỉ dùng khi backend WebSocket Matching Engine đã được triển khai.
     * Hiện tại heartbeat qua REST (DriverApiService.postLocation) là primary path.
     */
    suspend fun sendDriverLocation(latitude: Double, longitude: Double) {
        if (webSocket == null) connect()
        val payload = JSONObject()
            .put("lat", latitude)
            .put("lng", longitude)
            .toString()
        val frame = "SEND\n" +
            "destination:/app/driver.location\n" +
            "content-type:application/json\n\n" +
            payload +
            "\u0000"
        webSocket?.send(frame)
    }

    fun disconnect() {
        webSocket?.close(1000, "Driver offline")
        webSocket = null
    }
}
