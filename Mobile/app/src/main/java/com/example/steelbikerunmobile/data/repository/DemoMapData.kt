package com.example.steelbikerunmobile.data.repository

import com.example.steelbikerunmobile.domain.model.LatLng
import com.example.steelbikerunmobile.domain.model.NearbyDriver
import com.example.steelbikerunmobile.domain.model.SurgeZone
import kotlin.math.abs

object DemoMapData {
    val defaultPickup = LatLng(10.7769, 106.7009)

    val drivers = listOf(
        NearbyDriver("demo-driver-1", "An Nguyen", LatLng(10.7782, 106.6997), 4.9f, "51G-123.45", 0.4),
        NearbyDriver("demo-driver-2", "Binh Tran", LatLng(10.7749, 106.7031), 4.8f, "59X-456.78", 0.7),
        NearbyDriver("demo-driver-3", "Chi Le", LatLng(10.7805, 106.7043), 4.7f, "50N-888.12", 1.1)
    )

    val surgeZones = listOf(
        SurgeZone("demo-h3-89283082803ffff", LatLng(10.7769, 106.7009), 1.2),
        SurgeZone("demo-h3-8928308280fffff", LatLng(10.7812, 106.6958), 1.6),
        SurgeZone("demo-h3-89283082817ffff", LatLng(10.7719, 106.7062), 1.0)
    )

    fun pseudoH3Index(location: LatLng): String {
        val latBucket = abs((location.latitude * 10_000).toInt())
        val lngBucket = abs((location.longitude * 10_000).toInt())
        return "demo-h3-9-${latBucket.toString(16)}-${lngBucket.toString(16)}"
    }
}
