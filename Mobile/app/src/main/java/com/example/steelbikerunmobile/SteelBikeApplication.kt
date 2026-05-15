package com.example.steelbikerunmobile

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.maplibre.android.MapLibre

@HiltAndroidApp
class SteelBikeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialise MapLibre GL Native SDK early so that the rendering engine is
        // bootstrapped before any MapView composable is inflated.
        MapLibre.getInstance(this)
    }
}
