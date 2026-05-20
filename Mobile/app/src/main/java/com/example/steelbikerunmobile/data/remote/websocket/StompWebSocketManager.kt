package com.example.steelbikerunmobile.data.remote.websocket

import android.util.Log
import com.example.steelbikerunmobile.BuildConfig
import com.example.steelbikerunmobile.data.local.datastore.AuthPreferencesDataStore
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

/**
 * StompWebSocketManager — STOMP 1.2 client trên nền OkHttp WebSocket.
 *
 * Quản lý toàn bộ vòng đời kết nối WebSocket đến Backend:
 * - CONNECT với JWT token từ DataStore
 * - SUBSCRIBE vào các topic (driver trips, customer updates)
 * - Nhận MESSAGE và phát qua SharedFlow cho ViewModel lắng nghe
 * - SEND location heartbeat qua /app/driver.location
 *
 * Kênh chính:
 * - /topic/driver/{driverId} -> Nhận TripRequestMessage
 * - /topic/trip/{customerId} -> Nhận DriverFoundMessage, TripStatusMessage
 */
@Singleton
class StompWebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val dataStore: AuthPreferencesDataStore,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "StompWS"
        private const val STOMP_VERSION = "1.2"
        private val NULL_CHAR = "\u0000"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var subscriptionId = 0

    // Flow cho các message nhận được từ server
    private val _incomingMessages = MutableSharedFlow<StompMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<StompMessage> = _incomingMessages.asSharedFlow()

    // Flow cho trạng thái kết nối
    private val _connectionState = MutableSharedFlow<ConnectionState>(replay = 1, extraBufferCapacity = 8)
    val connectionState: SharedFlow<ConnectionState> = _connectionState.asSharedFlow()

    /**
     * Kết nối WebSocket và gửi STOMP CONNECT frame.
     * Hàm này sẽ suspend cho đến khi nhận được frame CONNECTED từ server.
     */
    suspend fun connect() {
        if (isConnected) return
        if (webSocket != null) {
            // Đang kết nối, chờ kết quả
            connectionState.first { it == ConnectionState.CONNECTED || it == ConnectionState.DISCONNECTED || it == ConnectionState.ERROR }
            return
        }

        val token = dataStore.tokenFlow.first()
        val wsUrl = BuildConfig.WS_URL

        Log.d(TAG, "Connecting to $wsUrl")
        _connectionState.emit(ConnectionState.CONNECTING)

        val requestBuilder = Request.Builder().url(wsUrl)
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        webSocket = okHttpClient.newWebSocket(
            requestBuilder.build(),
            object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket opened, sending STOMP CONNECT")
                    val connectHeaders = mutableMapOf(
                        "accept-version" to STOMP_VERSION,
                        "heart-beat" to "10000,10000"
                    )
                    // Gửi JWT trong STOMP CONNECT frame header
                    // StompJwtChannelInterceptor sẽ đọc và xác thực
                    token?.let { connectHeaders["Authorization"] = "Bearer $it" }
                    val connectFrame = buildFrame("CONNECT", connectHeaders)
                    ws.send(connectFrame)
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    handleStompFrame(text)
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket failure: ${t.message}")
                    isConnected = false
                    webSocket = null
                    scope.launch { _connectionState.emit(ConnectionState.DISCONNECTED) }
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closed: $code $reason")
                    isConnected = false
                    webSocket = null
                    scope.launch { _connectionState.emit(ConnectionState.DISCONNECTED) }
                }
            }
        )

        // Suspend chờ nhận được CONNECTED từ server
        connectionState.first { it == ConnectionState.CONNECTED || it == ConnectionState.DISCONNECTED || it == ConnectionState.ERROR }
    }

    /**
     * Đợi cho đến khi WebSocket đạt trạng thái CONNECTED.
     * Dùng khi cần đảm bảo WebSocket đã sẵn sàng trước khi thực hiện action khác.
     *
     * @param timeoutMillis thời gian tối đa chờ
     * @return true nếu đã CONNECTED, false nếu timeout hoặc lỗi
     */
    suspend fun waitForConnection(timeoutMillis: Long = 10_000L): Boolean {
        return withContext(Dispatchers.IO) {
            if (isConnected) return@withContext true
            try {
                val result = connectionState.first(timeoutMillis) {
                    it == ConnectionState.CONNECTED || it == ConnectionState.DISCONNECTED || it == ConnectionState.ERROR
                }
                result == ConnectionState.CONNECTED
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Subscribe vào một STOMP topic.
     * @return subscription ID (dùng để unsubscribe sau)
     */
    fun subscribe(destination: String): String {
        val subId = "sub-${subscriptionId++}"
        val frame = buildFrame(
            "SUBSCRIBE",
            mapOf(
                "id" to subId,
                "destination" to destination
            )
        )
        if (isConnected) {
            webSocket?.send(frame)
            Log.d(TAG, "SUBSCRIBE $destination (id=$subId)")
        } else {
            Log.w(TAG, "Cannot SUBSCRIBE $destination: STOMP is not connected!")
        }
        return subId
    }

    /**
     * Unsubscribe khỏi một topic.
     */
    fun unsubscribe(subscriptionId: String) {
        val frame = buildFrame("UNSUBSCRIBE", mapOf("id" to subscriptionId))
        webSocket?.send(frame)
        Log.d(TAG, "UNSUBSCRIBE id=$subscriptionId")
    }

    /**
     * Gửi message đến một destination (ví dụ /app/driver.location).
     */
    fun send(destination: String, body: Any) {
        val json = gson.toJson(body)
        val frame = buildFrame(
            "SEND",
            mapOf(
                "destination" to destination,
                "content-type" to "application/json"
            ),
            json
        )
        if (isConnected) {
            webSocket?.send(frame)
        } else {
            Log.w(TAG, "Cannot SEND to $destination: STOMP is not connected!")
        }
    }

    /**
     * Gửi location heartbeat qua WebSocket.
     */
    fun sendLocationHeartbeat(latitude: Double, longitude: Double, heading: Float? = null, speed: Float? = null) {
        val payload = buildMap {
            put("latitude", latitude)
            put("longitude", longitude)
            heading?.let { put("heading", it) }
            speed?.let { put("speed", it) }
        }
        send("/app/driver.location", payload)
    }

    /**
     * Ngắt kết nối và cleanup.
     */
    fun disconnect() {
        if (webSocket != null) {
            val frame = buildFrame("DISCONNECT", mapOf("receipt" to "disconnect-receipt"))
            webSocket?.send(frame)
            webSocket?.close(1000, "Client disconnect")
            webSocket = null
            isConnected = false
            scope.launch { _connectionState.emit(ConnectionState.DISCONNECTED) }
        }
        Log.d(TAG, "Disconnected")
    }

    // -- Private helpers --

    private fun handleStompFrame(raw: String) {
        val lines = raw.split("\n")
        if (lines.isEmpty()) return

        val command = lines[0].trim()

        when (command) {
            "CONNECTED" -> {
                isConnected = true
                Log.d(TAG, "STOMP CONNECTED")
                scope.launch { _connectionState.emit(ConnectionState.CONNECTED) }
            }
            "MESSAGE" -> {
                // Parse headers
                val headers = mutableMapOf<String, String>()
                var bodyStart = 1
                for (i in 1 until lines.size) {
                    val line = lines[i].trim()
                    if (line.isEmpty()) {
                        bodyStart = i + 1
                        break
                    }
                    val colonIndex = line.indexOf(':')
                    if (colonIndex > 0) {
                        headers[line.substring(0, colonIndex)] = line.substring(colonIndex + 1)
                    }
                }

                // Parse body (everything after empty line, remove NULL char)
                val body = lines.drop(bodyStart).joinToString("\n").replace(NULL_CHAR, "").trim()
                val destination = headers["destination"] ?: ""

                Log.d(TAG, "MESSAGE from $destination: ${body.take(100)}")

                scope.launch {
                    _incomingMessages.emit(
                        StompMessage(
                            destination = destination,
                            body = body,
                            headers = headers
                        )
                    )
                }
            }
            "ERROR" -> {
                Log.e(TAG, "STOMP ERROR: $raw")
                scope.launch { _connectionState.emit(ConnectionState.ERROR) }
            }
            "RECEIPT" -> {
                Log.d(TAG, "STOMP RECEIPT received")
            }
            // Heart-beat frames (empty lines) — ignore
            "" -> { /* no-op */ }
        }
    }

    private fun buildFrame(command: String, headers: Map<String, String>, body: String = ""): String {
        val sb = StringBuilder()
        sb.append(command).append("\n")
        headers.forEach { (key, value) ->
            sb.append("$key:$value\n")
        }
        sb.append("\n")
        sb.append(body)
        sb.append(NULL_CHAR)
        return sb.toString()
    }
}

/**
 * Gói tin STOMP nhận được từ server.
 */
data class StompMessage(
    val destination: String,
    val body: String,
    val headers: Map<String, String> = emptyMap()
)

/**
 * Trạng thái kết nối WebSocket.
 */
enum class ConnectionState {
    CONNECTING, CONNECTED, DISCONNECTED, ERROR
}
