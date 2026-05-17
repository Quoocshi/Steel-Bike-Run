package com.example.steelbikerunmobile.domain.usecase.trip

import com.example.steelbikerunmobile.data.remote.api.TripApiService
import com.example.steelbikerunmobile.domain.model.SurgeZone
import com.example.steelbikerunmobile.domain.model.LatLng
import javax.inject.Inject

/**
 * GetSurgeZonesUseCase — lấy danh sách vùng surge pricing từ backend.
 *
 * Được gọi khi customer mở bản đồ để hiển thị lớp hexagon màu sắc
 * theo mức surge.
 */
class GetSurgeZonesUseCase @Inject constructor(
    private val tripApiService: TripApiService
) {
    /**
     * Lấy surge zones từ backend và map về domain model.
     * Trả về tất cả các ô, kể cả surge=1.0 (bình thường)
     * để mobile tự quyết định màu sắc.
     */
    suspend operator fun invoke(): Result<List<SurgeZone>> = runCatching {
        tripApiService.getSurgeZones().data?.map { dto ->
            SurgeZone(
                h3Index  = dto.h3Index,
                center   = LatLng(dto.centerLat, dto.centerLng),
                surgeMultiplier = dto.surgeMultiplier.toDouble(),
            )
        } ?: emptyList()
    }
}
