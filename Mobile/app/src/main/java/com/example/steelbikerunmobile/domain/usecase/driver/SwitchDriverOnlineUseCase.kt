package com.example.steelbikerunmobile.domain.usecase.driver

import com.example.steelbikerunmobile.domain.model.VehicleInfo
import com.example.steelbikerunmobile.domain.repository.DriverRepository
import javax.inject.Inject

class SwitchDriverOnlineUseCase @Inject constructor(
    private val driverRepository: DriverRepository
) {
    suspend operator fun invoke(vehicleInfo: VehicleInfo?) = driverRepository.switchOnline(vehicleInfo)
}
