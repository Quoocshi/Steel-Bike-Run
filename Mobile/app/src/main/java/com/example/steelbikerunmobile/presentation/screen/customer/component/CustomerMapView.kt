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
import com.example.steelbikerunmobile.domain.model.SurgeZone
import com.example.steelbikerunmobile.presentation.screen.customer.home.CustomerFlowStep
import com.example.steelbikerunmobile.presentation.theme.CustomerPrimary
import com.example.steelbikerunmobile.presentation.theme.CustomerSecondary
import com.example.steelbikerunmobile.presentation.theme.DriverPrimary
import com.example.steelbikerunmobile.presentation.theme.ErrorRed
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolygonOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
    surgeZones: List<SurgeZone>,
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
    LaunchedEffect(trackedDriverLocation, flowStep, mapRef, recenterTrigger) {
        val map = mapRef ?: return@LaunchedEffect
        val target = when {
            flowStep == CustomerFlowStep.TRACKING && trackedDriverLocation != null ->
                trackedDriverLocation.toMapLibre()
            flowStep == CustomerFlowStep.TRIP_PREVIEW && destination != null ->
                LatLng(
                    (pickup.latitude + destination.latitude) / 2,
                    (pickup.longitude + destination.longitude) / 2,
                )
            else -> pickup.toMapLibre()
        }
        val zoom = if (flowStep == CustomerFlowStep.TRIP_PREVIEW) 13.5 else 15.0
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(target, zoom),
            800,
        )
    }

    // ── Markers & overlays ──────────────────────────────────────────────────
    LaunchedEffect(
        pickup, destination, nearbyDrivers, surgeZones,
        trackedDriverLocation, flowStep, mapRef
    ) {
        val map = mapRef ?: return@LaunchedEffect
        map.clear()

        val iconFactory = IconFactory.getInstance(context)
        val pickupBmp = createCircleMarkerBitmap(CustomerPrimary, "📍")
        val destBmp   = createCircleMarkerBitmap(ErrorRed, "🏁")
        val driverBmp = createCircleMarkerBitmap(CustomerSecondary, "🚲")
        val trackedBmp = createCircleMarkerBitmap(DriverPrimary, "🚲")

        // H3 surge zone hexagons
        surgeZones.forEach { zone ->
            val center = zone.center.toMapLibre()
            val vertices = hexagonVertices(center, radiusDeg = 0.0014)
            val fill = when {
                zone.surgeMultiplier >= 2.0 -> Color(0xCCE74C3C)
                zone.surgeMultiplier >= 1.5 -> Color(0x99E67E22)
                zone.surgeMultiplier >  1.0 -> Color(0x66F39C12)
                else                        -> Color(0x442ECC71)
            }
            map.addPolygon(
                PolygonOptions()
                    .addAll(vertices)
                    .fillColor(fill.toArgb())
                    .strokeColor(fill.copy(alpha = 0.9f).toArgb())
            )
        }

        // Nearby driver markers
        if (flowStep == CustomerFlowStep.HOME || flowStep == CustomerFlowStep.SEARCHING) {
            nearbyDrivers.forEach { driver ->
                map.addMarker(
                    MarkerOptions()
                        .position(driver.location.toMapLibre())
                        .icon(iconFactory.fromBitmap(driverBmp))
                        .title(driver.fullName)
                        .snippet("${driver.vehiclePlate ?: ""} · ${"%.1f".format(driver.rating)}★")
                )
            }
        }

        // Pickup marker
        map.addMarker(
            MarkerOptions()
                .position(pickup.toMapLibre())
                .icon(iconFactory.fromBitmap(pickupBmp))
                .title("Điểm đón của bạn")
        )

        // Destination marker
        if (destination != null && flowStep.ordinal >= CustomerFlowStep.TRIP_PREVIEW.ordinal) {
            map.addMarker(
                MarkerOptions()
                    .position(destination.toMapLibre())
                    .icon(iconFactory.fromBitmap(destBmp))
                    .title("Điểm đến")
            )
        }

        // Tracked driver marker
        if (flowStep == CustomerFlowStep.TRACKING && trackedDriverLocation != null) {
            map.addMarker(
                MarkerOptions()
                    .position(trackedDriverLocation.toMapLibre())
                    .icon(iconFactory.fromBitmap(trackedBmp))
                    .title("Tài xế của bạn")
            )
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

/** Compute 6 vertices of a flat-top hexagon around a MapLibre center. */
private fun hexagonVertices(center: LatLng, radiusDeg: Double): List<LatLng> {
    val cosLat = cos(Math.toRadians(center.latitude))
    return (0 until 6).map { i ->
        val angleDeg = 60.0 * i - 30.0
        val angleRad = angleDeg * PI / 180.0
        LatLng(
            center.latitude  + radiusDeg * cos(angleRad),
            center.longitude + radiusDeg * sin(angleRad) / cosLat,
        )
    }
}
