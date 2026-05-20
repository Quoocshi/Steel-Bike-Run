package com.example.steelbikerunmobile.domain.usecase.trip

import com.example.steelbikerunmobile.data.remote.websocket.StompWebSocketManager
import com.google.gson.Gson
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Test suite cho ObserveDriverTripRequestsUseCase — Fix 3:
 * connectAndWaitForConnection() đảm bảo WebSocket CONNECTED trước khi subscribe.
 *
 * Bug gốc: startListeningForTrips() gọi subscribe() mà không đợi WebSocket ready.
 * Nếu subscription được gửi trước STOMP CONNECTED frame, server bỏ qua
 * subscription → driver không nhận cuốc tiếp theo sau COMPLETED.
 */
class ObserveDriverTripRequestsUseCaseTest {

    private lateinit var stompManager: StompWebSocketManager
    private lateinit var useCase: ObserveDriverTripRequestsUseCase
    private val gson = Gson()
    private val driverId = "driver-uuid-123"

    @Before
    fun setup() {
        stompManager = mockk(relaxed = true)
        useCase = ObserveDriverTripRequestsUseCase(stompManager, gson)
    }

    @Test
    fun `subscribe calls connect then subscribe on StompWebSocketManager`() = runTest {
        useCase.subscribe(driverId)

        coVerifySequence {
            stompManager.connect()
            stompManager.subscribe("/topic/driver/$driverId")
        }
    }

    @Test
    fun `connectAndWaitForConnection returns true when WebSocket connects successfully`() = runTest {
        every { stompManager.waitForConnection(any()) } returns true

        val result = useCase.connectAndWaitForConnection()

        assertTrue(result)
        coVerify { stompManager.connect() }
        coVerify { stompManager.waitForConnection(10_000L) }
    }

    @Test
    fun `connectAndWaitForConnection returns false when WebSocket times out`() = runTest {
        every { stompManager.waitForConnection(any()) } returns false

        val result = useCase.connectAndWaitForConnection(5_000L)

        assertFalse(result)
        coVerify { stompManager.connect() }
        coVerify { stompManager.waitForConnection(5_000L) }
    }

    @Test
    fun `connectAndWaitForConnection uses default 10 second timeout`() = runTest {
        every { stompManager.waitForConnection(any()) } returns true

        useCase.connectAndWaitForConnection()

        coVerify { stompManager.waitForConnection(10_000L) }
    }

    @Test
    fun `connectAndWaitForConnection delegates timeout argument correctly`() = runTest {
        every { stompManager.waitForConnection(any()) } returns true

        useCase.connectAndWaitForConnection(30_000L)

        coVerify { stompManager.waitForConnection(30_000L) }
    }
}
