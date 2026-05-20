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

        // Track whether we've already emitted the first location to avoid duplicates.
        // requestSingleUpdate and requestLocationUpdates can both fire for the same GPS fix.
        @Suppress("LocalVariableName")
        var _emitted = false
        fun markEmitted() { _emitted = true }

        val singleUpdateListener = object : LocationListener {
            // FIRES FIRST: Most devices return a GPS fix within milliseconds of
            // requestSingleUpdate, before the periodic updates start firing.
            // This solves the "first online switch" race condition where
            // lastKnownLocation is null (cold GPS start).
            override fun onLocationChanged(location: Location) {
                if (_emitted) return
                markEmitted()
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

        val periodicUpdateListener = object : LocationListener {
            // FIRES SECOND (or first on some devices): Regular interval updates.
            // Also catches the case where requestSingleUpdate didn't fire on this device.
            override fun onLocationChanged(location: Location) {
                if (_emitted) {
                    trySend(
                        LocationHeartbeat(
                            location = LatLng(location.latitude, location.longitude),
                            heading = location.bearing.takeIf { location.hasBearing() },
                            speedMetersPerSecond = location.speed.takeIf { location.hasSpeed() }
                        )
                    )
                }
            }
            @Deprecated("Deprecated in Android SDK")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> LocationManager.NETWORK_PROVIDER
        }

        // PHASE 1: Force immediate GPS fix. Solves cold-start / null lastKnownLocation issue.
        // On most devices this fires within milliseconds. On slow devices it may take a few seconds.
        locationManager.requestSingleUpdate(provider, singleUpdateListener, null)

        // PHASE 2: Regular interval updates as backup / continuation.
        // Also fires the first location on some devices where requestSingleUpdate is not reliable.
        locationManager.requestLocationUpdates(provider, intervalMillis, 5f, periodicUpdateListener)

        awaitClose {
            locationManager.removeUpdates(singleUpdateListener)
            locationManager.removeUpdates(periodicUpdateListener)
        }
    }
}
