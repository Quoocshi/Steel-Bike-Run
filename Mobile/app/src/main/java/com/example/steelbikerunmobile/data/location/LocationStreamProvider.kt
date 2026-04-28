package com.example.steelbikerunmobile.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.example.steelbikerunmobile.domain.model.LatLng
import com.example.steelbikerunmobile.domain.model.LocationHeartbeat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationStreamProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun observeLocation(intervalMillis: Long = 3_000L): Flow<LocationHeartbeat> = callbackFlow {
        if (!hasLocationPermission()) {
            close(SecurityException("Ứng dụng cần quyền vị trí để stream GPS"))
            return@callbackFlow
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(
                    LocationHeartbeat(
                        location = LatLng(location.latitude, location.longitude),
                        heading = location.bearing.takeIf { location.hasBearing() },
                        speedMetersPerSecond = location.speed.takeIf { location.hasSpeed() }
                    )
                )
            }

            @Deprecated("Deprecated in Android SDK")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> LocationManager.NETWORK_PROVIDER
        }
        val lastKnown = runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        lastKnown?.let { listener.onLocationChanged(it) }
        locationManager.requestLocationUpdates(provider, intervalMillis, 5f, listener)

        awaitClose { locationManager.removeUpdates(listener) }
    }
}
