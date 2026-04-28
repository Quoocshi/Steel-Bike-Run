package com.example.steelbikerunmobile.data.remote.websocket

import com.example.steelbikerunmobile.BuildConfig
import com.example.steelbikerunmobile.data.local.datastore.AuthPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val dataStore: AuthPreferencesDataStore
) {
    private var webSocket: WebSocket? = null

    fun connect() {
        if (webSocket != null) return
        val token = runBlocking { dataStore.tokenFlow.first() }
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

    fun sendDriverLocation(latitude: Double, longitude: Double) {
        connect()
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
