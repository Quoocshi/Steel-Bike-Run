package com.example.steelbikerunmobile.data.repository

import com.example.steelbikerunmobile.data.remote.NetworkErrorMapper
import com.example.steelbikerunmobile.data.remote.api.TripApiService
import com.example.steelbikerunmobile.data.remote.dto.CreateTripRequestDto
import com.example.steelbikerunmobile.data.remote.dto.PriceEstimateDto
import com.example.steelbikerunmobile.data.remote.dto.PriceEstimateRequestDto
import com.example.steelbikerunmobile.domain.model.BookingDraft
import com.example.steelbikerunmobile.domain.model.PriceEstimate
import com.example.steelbikerunmobile.domain.repository.TripRepository
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class TripRepositoryImpl @Inject constructor(
    private val tripApiService: TripApiService
) : TripRepository {

    override suspend fun estimate(draft: BookingDraft): Result<PriceEstimate> =
        NetworkErrorMapper.safeCall {
            val envelope = tripApiService.estimate(draft.toEstimateDto())
            envelope.data?.toDomain()
                ?: error(envelope.message?.takeIf { it.isNotBlank() } ?: "Không thể tính giá")
        }.recover { estimateLocally(draft) }

    override suspend fun createTrip(draft: BookingDraft): Result<String> =
        NetworkErrorMapper.safeCall {
            val envelope = tripApiService.createTrip(
                CreateTripRequestDto(
                    pickupLat = draft.pickup.latitude,
                    pickupLng = draft.pickup.longitude,
                    destLat = draft.destination.latitude,
                    destLng = draft.destination.longitude,
                    destAddress = draft.destinationAddress
                )
            )
            envelope.data?.id ?: error(envelope.message ?: "Không thể đặt xe")
        }

    override suspend fun acceptTrip(tripId: String): Result<Unit> =
        NetworkErrorMapper.safeCall {
            tripApiService.acceptTrip(tripId)
            Unit
        }

    override suspend fun arriveAtPickup(tripId: String): Result<Unit> =
        NetworkErrorMapper.safeCall {
            tripApiService.arriveAtPickup(tripId)
            Unit
        }

    override suspend fun startTrip(tripId: String): Result<Unit> =
        NetworkErrorMapper.safeCall {
            tripApiService.startTrip(tripId)
            Unit
        }

    override suspend fun completeTrip(tripId: String): Result<Unit> =
        NetworkErrorMapper.safeCall {
            tripApiService.completeTrip(tripId)
            Unit
        }

    override suspend fun cancelTrip(tripId: String): Result<Unit> =
        NetworkErrorMapper.safeCall {
            tripApiService.cancelTrip(tripId)
            Unit
        }

    private fun BookingDraft.toEstimateDto(): PriceEstimateRequestDto {
        return PriceEstimateRequestDto(
            pickupLat = pickup.latitude,
            pickupLng = pickup.longitude,
            destLat = destination.latitude,
            destLng = destination.longitude,
            destAddress = destinationAddress
        )
    }

    private fun PriceEstimateDto.toDomain(): PriceEstimate {
        return PriceEstimate(
            basePrice = basePrice ?: 12_000.0,
            surgeMultiplier = surgeMultiplier ?: 1.0,
            finalPrice = finalPrice ?: 12_000.0,
            distanceKm = distanceKm ?: 0.0,
            durationMinutes = durationMinutes ?: 0
        )
    }

    private fun estimateLocally(draft: BookingDraft): PriceEstimate {
        val distanceKm = haversineKm(
            draft.pickup.latitude,
            draft.pickup.longitude,
            draft.destination.latitude,
            draft.destination.longitude
        ).coerceAtLeast(0.5)
        val basePrice = 12_000.0 + distanceKm * 5_000.0
        val surge = DemoMapData.surgeZones.maxOf { it.surgeMultiplier }
        return PriceEstimate(
            basePrice = basePrice,
            surgeMultiplier = surge,
            finalPrice = basePrice * surge,
            distanceKm = distanceKm,
            durationMinutes = (distanceKm / 25.0 * 60).toInt().coerceAtLeast(3)
        )
    }

    private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val radiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return radiusKm * c
    }
}
