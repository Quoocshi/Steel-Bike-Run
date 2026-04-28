package com.example.steelbikerunmobile.domain.usecase.driver

import com.example.steelbikerunmobile.domain.repository.DriverRepository
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class StreamLocationUseCase @Inject constructor(
    private val driverRepository: DriverRepository
) {
    operator fun invoke() = driverRepository.observeLocation()
        .onEach { heartbeat -> driverRepository.sendLocationHeartbeat(heartbeat) }

    fun stop() {
        driverRepository.stopRealtime()
    }
}
