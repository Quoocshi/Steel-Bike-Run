package com.example.steelbikerunmobile.domain.usecase.driver

import com.example.steelbikerunmobile.domain.repository.DriverRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Stream H3 cell index hiện tại của tài xế từ server.
 * Emit sau mỗi heartbeat thành công (mỗi ~3 giây khi online).
 * Giá trị null khi tài xế offline hoặc chưa có heartbeat đầu tiên.
 */
class ObserveCurrentH3IndexUseCase @Inject constructor(
    private val driverRepository: DriverRepository
) {
    operator fun invoke(): Flow<String?> = driverRepository.observeCurrentH3Index()
}
