package com.example.steelbikerunmobile.domain.usecase.driver

import com.example.steelbikerunmobile.domain.repository.DriverRepository
import javax.inject.Inject

class GetDriverProfileUseCase @Inject constructor(
    private val driverRepository: DriverRepository
) {
    suspend operator fun invoke() = driverRepository.getProfile()
}
