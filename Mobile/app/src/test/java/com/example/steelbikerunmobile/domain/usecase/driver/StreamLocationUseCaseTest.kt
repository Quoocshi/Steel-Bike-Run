package com.example.steelbikerunmobile.domain.usecase.driver

import com.example.steelbikerunmobile.domain.model.LatLng
import com.example.steelbikerunmobile.domain.model.LocationHeartbeat
import com.example.steelbikerunmobile.domain.repository.DriverRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Test suite cho StreamLocationUseCase — Fix 2: connectWebSocket-first.
 *
 * CRITICAL: StreamLocationUseCase.connectWebSocket() phải được gọi TRƯỚC khi
 * Flow bắt đầu. Nếu heartbeat được gửi trước khi WebSocket CONNECTED,
 * message sẽ bị drop và MatchingEngine không tìm thấy driver.
 */
class StreamLocationUseCaseTest {

    private lateinit var driverRepository: DriverRepository
    private lateinit var useCase: StreamLocationUseCase

    @Before
    fun setup() {
        driverRepository = mockk(relaxed = true)
        useCase = StreamLocationUseCase(driverRepository)
    }

    @Test
    fun `connectWebSocket delegates to driverRepository`() = runTest {
        useCase.connectWebSocket()
        coVerify { driverRepository.connectWebSocket() }
    }

    @Test
    fun `stop calls stopRealtime on repository`() = runTest {
        useCase.stop()
        coVerify { driverRepository.stopRealtime() }
    }

    @Test
    fun `invoke returns a non-null Flow from observeLocation`() {
        val mockFlow: Flow<LocationHeartbeat> = flowOf(
            LocationHeartbeat(LatLng(10.7727, 106.6980), null, null)
        )
        every { driverRepository.observeLocation() } returns mockFlow

        val result = useCase()

        assertNotNull(result)
        assertTrue(result is Flow<*>)
    }
}
