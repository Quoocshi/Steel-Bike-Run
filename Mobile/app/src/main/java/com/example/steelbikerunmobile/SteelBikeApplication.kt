package com.example.steelbikerunmobile

import android.app.Application
import android.util.Log
import com.google.android.gms.maps.MapsInitializer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SteelBikeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Eagerly initialise the Google Maps Android SDK so that BitmapDescriptorFactory
        // (used by our custom marker icons) is ready before any composable tries to build a
        // BitmapDescriptor. Without this, `BitmapDescriptorFactory.fromBitmap(...)` throws
        // `IBitmapDescriptorFactory is not initialized` whenever it is called from within a
        // `remember { ... }` block (i.e. before the first GoogleMap composable has a chance
        // to render and bootstrap the SDK).
        @Suppress("DEPRECATION")
        runCatching {
            // Synchronous variant: guarantees BitmapDescriptorFactory is usable as soon as
            // this returns. Costs ~50ms on cold start which is acceptable.
            MapsInitializer.initialize(this)
        }.onFailure { t ->
            Log.w("SteelBikeApp", "Maps SDK initialisation failed: ${t.message}")
        }
    }
}
