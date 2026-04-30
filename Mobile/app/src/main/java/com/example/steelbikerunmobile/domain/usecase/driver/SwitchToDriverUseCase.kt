package com.example.steelbikerunmobile.domain.usecase.driver

import com.example.steelbikerunmobile.domain.model.DriverProfile
import com.example.steelbikerunmobile.domain.model.VehicleInfo
import com.example.steelbikerunmobile.domain.repository.DriverRepository
import javax.inject.Inject

/**
 * CUSTOMER → DRIVER role switch. Invokes /driver/switch on the backend.
 *
 * Pass `vehicleInfo` only on the first switch (when no driver profile exists).
 * On subsequent switches the backend ignores the body, so `null` is fine.
 *
 * On success the repository persists the rotated JWT before returning, so the
 * caller can immediately invoke driver-only APIs.
 */
class SwitchToDriverUseCase @Inject constructor(
    private val driverRepository: DriverRepository
) {
    suspend operator fun invoke(vehicleInfo: VehicleInfo? = null): Result<DriverProfile> =
        driverRepository.switchToDriver(vehicleInfo)
}
