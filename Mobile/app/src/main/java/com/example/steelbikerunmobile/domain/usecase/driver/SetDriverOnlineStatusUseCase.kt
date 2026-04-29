package com.example.steelbikerunmobile.domain.usecase.driver

import com.example.steelbikerunmobile.domain.model.DriverProfile
import com.example.steelbikerunmobile.domain.repository.DriverRepository
import javax.inject.Inject

/**
 * Toggle the driver's online flag while staying in Driver mode.
 * Used for the "Bắt Đầu Nhận Cuốc" / "Kết Thúc Ca" buttons.
 *
 * Does NOT change the role or rotate the JWT — for that, use
 * [SwitchToDriverUseCase] / [SwitchToCustomerUseCase].
 */
class SetDriverOnlineStatusUseCase @Inject constructor(
    private val driverRepository: DriverRepository
) {
    suspend operator fun invoke(isOnline: Boolean): Result<DriverProfile> =
        driverRepository.setOnlineStatus(isOnline)
}
