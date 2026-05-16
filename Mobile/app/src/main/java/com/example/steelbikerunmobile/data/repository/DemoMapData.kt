package com.example.steelbikerunmobile.data.repository

import com.example.steelbikerunmobile.domain.model.LatLng
import com.example.steelbikerunmobile.domain.model.NearbyDriver
import com.example.steelbikerunmobile.domain.model.SurgeZone
import kotlin.math.abs

object DemoMapData {
    val defaultPickup = LatLng(10.7769, 106.7009)

    val drivers = emptyList<NearbyDriver>()

    val surgeZones = try {
        val h3 = com.uber.h3core.H3Core.newInstance()
        // Center around defaultPickup
        val centerIndex = h3.latLngToCell(defaultPickup.latitude, defaultPickup.longitude, 8)
        
        // Generate a grid of k=2 (approx 19 cells)
        val cells = h3.gridDisk(centerIndex, 2)
        
        cells.mapIndexed { index, cellId ->
            val center = h3.cellToLatLng(cellId)
            SurgeZone(
                h3Index = h3.h3ToString(cellId),
                center = LatLng(center.lat, center.lng),
                surgeMultiplier = 1.0 + (index % 5) * 0.2 // Random multipliers 1.0, 1.2, 1.4, 1.6, 1.8
            )
        }
    } catch (e: Throwable) {
        // Fallback if H3 native lib is missing (UnsatisfiedLinkError is a Throwable, not Exception)
        listOf(
            SurgeZone("demo-fallback-1", LatLng(10.7769, 106.7009), 1.2),
            SurgeZone("demo-fallback-2", LatLng(10.7812, 106.6958), 1.6),
            SurgeZone("demo-fallback-3", LatLng(10.7719, 106.7062), 1.0)
        )
    }

    fun pseudoH3Index(location: LatLng): String {
        return try {
            val h3 = com.uber.h3core.H3Core.newInstance()
            h3.latLngToCellAddress(location.latitude, location.longitude, 9)
        } catch (e: Throwable) {
            val latBucket = kotlin.math.abs((location.latitude * 10_000).toInt())
            val lngBucket = kotlin.math.abs((location.longitude * 10_000).toInt())
            "demo-h3-9-${latBucket.toString(16)}-${lngBucket.toString(16)}"
        }
    }
}
