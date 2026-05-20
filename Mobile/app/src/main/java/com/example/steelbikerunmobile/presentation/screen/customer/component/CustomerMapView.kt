package com.example.steelbikerunmobile.presentation.screen.customer.component

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.steelbikerunmobile.BuildConfig
import com.example.steelbikerunmobile.domain.model.LatLng as DomainLatLng
import com.example.steelbikerunmobile.domain.model.NearbyDriver
import com.example.steelbikerunmobile.presentation.screen.customer.home.CustomerFlowStep
import com.example.steelbikerunmobile.presentation.theme.CustomerPrimary
import com.example.steelbikerunmobile.presentation.theme.CustomerSecondary
import com.example.steelbikerunmobile.presentation.theme.DriverPrimary
import com.example.steelbikerunmobile.presentation.theme.ErrorRed
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

// ── Goong Map style URL ───────────────────────────────────────────────────────
private val STYLE_URL: String
    get() = "https://tiles.goong.io/assets/goong_map_web.json?api_key=${BuildConfig.GOONG_MAP_KEY}"

// ── Extension: domain LatLng → MapLibre LatLng ────────────────────────────────
private fun DomainLatLng.toMapLibre() = LatLng(latitude, longitude)

@Composable
fun CustomerMapView(
    pickup: DomainLatLng,
    destination: DomainLatLng?,
    nearbyDrivers: List<NearbyDriver>,
    trackedDriverLocation: DomainLatLng?,
    flowStep: CustomerFlowStep,
    recenterTrigger: Long = 0L,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialise MapLibre once per process
    remember { MapLibre.getInstance(context) }

    // Hold MapLibreMap reference once the async callback completes
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }

    // Track markers individually instead of clearing all
    var pickupMarker by remember { mutableStateOf<Marker?>(null) }
    var destinationMarker by remember { mutableStateOf<Marker?>(null) }
    var trackedDriverMarker by remember { mutableStateOf<Marker?>(null) }
    val nearbyDriverMarkers = remember { mutableStateOf<List<Marker>>(emptyList()) }

    // Bitmap caches to avoid recreating on every recomposition
    val iconFactory by remember { mutableStateOf(IconFactory.getInstance(context)) }
    val pickupBmp = remember { createCircleMarkerBitmap(CustomerPrimary, "📍") }
    val destBmp = remember { createCircleMarkerBitmap(ErrorRed, "🏁") }
    val driverBmp = remember { createCircleMarkerBitmap(CustomerSecondary, "🚲") }
    val trackedBmp = remember { createCircleMarkerBitmap(DriverPrimary, "🚲") }

    val mapView = remember {
        MapView(context).apply {
            getMapAsync { mlMap ->
                mlMap.setStyle(Style.Builder().fromUri(STYLE_URL)) {
                    mlMap.uiSettings.apply {
                        isCompassEnabled = false
                    }
                    mlMap.cameraPosition = CameraPosition.Builder()
                        .target(pickup.toMapLibre())
                        .zoom(15.0)
                        .build()
                }
                mapRef = mlMap
            }
        }
    }

    // ── Lifecycle bridging ──────────────────────────────────────────────────
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START   -> mapView.onStart()
                Lifecycle.Event.ON_RESUME  -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE   -> mapView.onPause()
                Lifecycle.Event.ON_STOP    -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    // ── Camera animation ────────────────────────────────────────────────────
    // Only animate camera on flow step change or user recenter request.
    // NOT on every driver location update — that would interrupt tile loading.
    LaunchedEffect(flowStep, mapRef, recenterTrigger) {
        val map = mapRef ?: return@LaunchedEffect

        // Determine initial position for this flow step
        val target = when (flowStep) {
            CustomerFlowStep.TRIP_PREVIEW -> {
                if (destination != null) {
                    LatLng(
                        (pickup.latitude + destination.latitude) / 2,
                        (pickup.longitude + destination.longitude) / 2,
                    )
                } else pickup.toMapLibre()
            }
            CustomerFlowStep.TRACKING -> {
                // Center on pickup when first entering TRACKING
                pickup.toMapLibre()
            }
            else -> pickup.toMapLibre()
        }

        val zoom = if (flowStep == CustomerFlowStep.TRIP_PREVIEW) 13.5 else 15.0
        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(target, zoom),
        )
    }

    // ── Core markers setup (pickup, destination) — only on step change ─────
    LaunchedEffect(pickup, destination, flowStep, mapRef) {
        val map = mapRef ?: return@LaunchedEffect

        // Pickup marker — always present
        pickupMarker?.remove()
        pickupMarker = map.addMarker(
            MarkerOptions()
                .position(pickup.toMapLibre())
                .icon(iconFactory.fromBitmap(pickupBmp))
                .title("Điểm đón của bạn")
        )

        // Destination marker — only when needed
        destinationMarker?.remove()
        destinationMarker = if (destination != null && flowStep.ordinal >= CustomerFlowStep.TRIP_PREVIEW.ordinal) {
            map.addMarker(
                MarkerOptions()
                    .position(destination.toMapLibre())
                    .icon(iconFactory.fromBitmap(destBmp))
                    .title("Điểm đến")
            )
        } else null
    }

    // ── Nearby driver markers — only on HOME/SEARCHING ─────────────────────
    LaunchedEffect(nearbyDrivers, flowStep, mapRef) {
        val map = mapRef ?: return@LaunchedEffect

        // Only show nearby drivers on HOME or SEARCHING steps
        if (flowStep == CustomerFlowStep.HOME || flowStep == CustomerFlowStep.SEARCHING) {
            // Remove old nearby markers
            nearbyDriverMarkers.value.forEach { it.remove() }

            // Add new nearby markers
            nearbyDriverMarkers.value = nearbyDrivers.map { driver ->
                map.addMarker(
                    MarkerOptions()
                        .position(driver.location.toMapLibre())
                        .icon(iconFactory.fromBitmap(driverBmp))
                        .title(driver.fullName)
                        .snippet("${driver.vehiclePlate ?: ""} · ${"%.1f".format(driver.rating)}★")
                )
            }
        } else {
            // Remove all nearby driver markers when not on HOME/SEARCHING
            nearbyDriverMarkers.value.forEach { it.remove() }
            nearbyDriverMarkers.value = emptyList()
        }
    }

    // ── Tracked driver marker — update position without clearing ────────────
    LaunchedEffect(trackedDriverLocation, flowStep, mapRef) {
        val map = mapRef ?: return@LaunchedEffect

        when {
            flowStep == CustomerFlowStep.TRACKING && trackedDriverLocation != null -> {
                if (trackedDriverMarker == null) {
                    // First time — create marker
                    trackedDriverMarker = map.addMarker(
                        MarkerOptions()
                            .position(trackedDriverLocation.toMapLibre())
                            .icon(iconFactory.fromBitmap(trackedBmp))
                            .title("Tài xế của bạn")
                    )
                } else {
                    // Update existing marker position
                    trackedDriverMarker?.position = trackedDriverLocation.toMapLibre()
                }
            }
            else -> {
                // Remove marker when not tracking
                trackedDriverMarker?.remove()
                trackedDriverMarker = null
            }
        }
    }

    // ── Render MapView ──────────────────────────────────────────────────────
    AndroidView(
        factory = { mapView },
        modifier = modifier,
    )
}

// ── Helpers ────────────────────────────────────────────────────────────────────

/** Create a 96×96 circular bitmap marker with an emoji label. */
private fun createCircleMarkerBitmap(bgColor: Color, emoji: String): Bitmap {
    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // Background circle
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bgColor.toArgb()
        style = Paint.Style.FILL
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    val half = size / 2f
    canvas.drawCircle(half, half, half - 4f, bgPaint)
    canvas.drawCircle(half, half, half - 4f, strokePaint)

    // Emoji text
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 36f
        typeface = Typeface.DEFAULT
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText(emoji, half, half + 14f, textPaint)

    return bitmap
}

