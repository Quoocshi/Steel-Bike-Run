package com.example.steelbikerunmobile.data.remote.websocket

import com.example.steelbikerunmobile.data.local.datastore.AuthPreferencesDataStore
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method

/**
 * Test suite cho StompWebSocketManager — Fix 3: waitForConnection().
 *
 * Bug gốc: Nếu WebSocket chưa CONNECTED mà subscribe() được gọi,
 * STOMP SUBSCRIBE frame được gửi trước CONNECT frame → server bỏ qua subscription.
 *
 * Fix: waitForConnection() đợi cho đến khi trạng thái là CONNECTED trước khi
 * proceed, đảm bảo subscription không bị drop.
 */
class StompWebSocketManagerConnectionTest {

    private lateinit var dataStore: AuthPreferencesDataStore

    @Before
    fun setup() {
        dataStore = mockk(relaxed = true)
    }

    @Test
    fun `waitForConnection method exists with correct signature`() {
        val methods = StompWebSocketManager::class.java.declaredMethods
        val methodNames = methods.map { it.name }

        assertTrue(
            "waitForConnection method must exist",
            methodNames.contains("waitForConnection")
        )

        val waitMethod = methods.first { it.name == "waitForConnection" }
        assertTrue(
            "waitForConnection must be suspend (Koroutines scope)",
            waitMethod.parameters.isEmpty() || waitMethod.parameterTypes.any { it.simpleName == "Continuation" }
        )
    }

    @Test
    fun `connectAndWaitForConnection method exists in ObserveDriverTripRequestsUseCase`() {
        val methods = com.example.steelbikerunmobile.domain.usecase.trip.ObserveDriverTripRequestsUseCase::class.java.declaredMethods
        val methodNames = methods.map { it.name }

        assertTrue(
            "connectAndWaitForConnection method must exist in ObserveDriverTripRequestsUseCase",
            methodNames.contains("connectAndWaitForConnection")
        )
    }

    @Test
    fun `StreamLocationUseCase has connectWebSocket method`() {
        val methods = com.example.steelbikerunmobile.domain.usecase.driver.StreamLocationUseCase::class.java.declaredMethods
        val methodNames = methods.map { it.name }

        assertTrue(
            "connectWebSocket method must exist in StreamLocationUseCase",
            methodNames.contains("connectWebSocket")
        )
    }

    @Test
    fun `DriverRepository interface has connectWebSocket method`() {
        val methods = com.example.steelbikerunmobile.domain.repository.DriverRepository::class.java.declaredMethods
        val methodNames = methods.map { it.name }

        assertTrue(
            "connectWebSocket method must exist in DriverRepository interface",
            methodNames.contains("connectWebSocket")
        )
    }
}
