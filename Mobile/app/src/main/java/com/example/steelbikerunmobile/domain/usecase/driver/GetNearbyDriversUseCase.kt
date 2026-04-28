package com.example.steelbikerunmobile.domain.usecase.driver

import com.example.steelbikerunmobile.domain.model.LatLng
import com.example.steelbikerunmobile.domain.repository.DriverRepository
import javax.inject.Inject

class GetNearbyDriversUseCase @Inject constructor(
    private val driverRepository: DriverRepository
) {
    suspend operator fun invoke(location: LatLng) =
        driverRepository.getNearbyDrivers(location.latitude, location.longitude)
}
