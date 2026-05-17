package com.example.steelbikerunmobile.data.repository

import com.example.steelbikerunmobile.domain.model.LatLng
import com.example.steelbikerunmobile.domain.model.NearbyDriver

object DemoMapData {
    val defaultPickup = LatLng(10.7769, 106.7009)

    val drivers = emptyList<NearbyDriver>()

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
