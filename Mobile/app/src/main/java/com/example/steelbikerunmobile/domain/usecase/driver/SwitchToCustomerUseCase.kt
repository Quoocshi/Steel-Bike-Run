package com.example.steelbikerunmobile.domain.usecase.driver

import com.example.steelbikerunmobile.domain.model.DriverProfile
import com.example.steelbikerunmobile.domain.repository.DriverRepository
import javax.inject.Inject

/**
 * DRIVER → CUSTOMER role switch. Invokes /driver/switch-back on the backend.
 *
 * The driver is automatically forced offline server-side. On success the
 * repository persists the rotated JWT (role=CUSTOMER) before returning, so
 * the next API call goes out with customer permissions.
 */
class SwitchToCustomerUseCase @Inject constructor(
    private val driverRepository: DriverRepository
) {
    suspend operator fun invoke(): Result<DriverProfile> =
        driverRepository.switchToCustomer()
}
