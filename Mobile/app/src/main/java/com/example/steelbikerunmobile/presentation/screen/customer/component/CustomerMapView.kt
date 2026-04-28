package com.example.steelbikerunmobile.presentation.screen.customer.component

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.steelbikerunmobile.domain.model.LatLng as DomainLatLng
import com.example.steelbikerunmobile.domain.model.NearbyDriver
import com.example.steelbikerunmobile.domain.model.SurgeZone
import com.example.steelbikerunmobile.presentation.screen.customer.home.CustomerFlowStep
import com.example.steelbikerunmobile.presentation.theme.CustomerPrimary
import com.example.steelbikerunmobile.presentation.theme.CustomerSecondary
import com.example.steelbikerunmobile.presentation.theme.DriverPrimary
import com.example.steelbikerunmobile.presentation.theme.ErrorRed
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng as GmsLatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ── Extension: domain LatLng → GMS LatLng ─────────────────────────────────────
fun DomainLatLng.toGms() = GmsLatLng(latitude, longitude)

@Composable
fun CustomerMapView(
    pickup: DomainLatLng,
    destination: DomainLatLng?,
    nearbyDrivers: List<NearbyDriver>,
    surgeZones: List<SurgeZone>,
    trackedDriverLocation: DomainLatLng?,
    flowStep: CustomerFlowStep,
    modifier: Modifier = Modifier,
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(pickup.toGms(), 15f)
    }

    // Animate camera: follow tracked driver, else center on pickup
    LaunchedEffect(trackedDriverLocation, flowStep) {
        val target = when {
            flowStep == CustomerFlowStep.TRACKING && trackedDriverLocation != null ->
                trackedDriverLocation.toGms()
            flowStep == CustomerFlowStep.TRIP_PREVIEW && destination != null ->
                GmsLatLng(
                    (pickup.latitude  + destination.latitude)  / 2,
                    (pickup.longitude + destination.longitude) / 2,
                )
            else -> pickup.toGms()
        }
        val zoom = if (flowStep == CustomerFlowStep.TRIP_PREVIEW) 13.5f else 15f
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(target, zoom),
            durationMs = 800,
        )
    }

    // Precompute custom marker icons (stable across recompositions)
    val pickupIcon    = remember { createCircleMarker(CustomerPrimary, "📍") }
    val destIcon      = remember { createCircleMarker(ErrorRed, "🏁") }
    val driverIcon    = remember { createCircleMarker(CustomerSecondary, "🚲") }
    val trackedIcon   = remember { createCircleMarker(DriverPrimary, "🚲") }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = false),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false,
        ),
    ) {
        // ── H3 surge zone hexagon polygons ─────────────────────────────────────
        surgeZones.forEach { zone ->
            val vertices = hexagonVertices(zone.center.toGms(), radiusDeg = 0.0014)
            val fill = when {
                zone.surgeMultiplier >= 2.0 -> Color(0xCCE74C3C)
                zone.surgeMultiplier >= 1.5 -> Color(0x99E67E22)
                zone.surgeMultiplier >  1.0 -> Color(0x66F39C12)
                else                        -> Color(0x442ECC71)
            }
            Polygon(
                points       = vertices,
                fillColor    = fill,
                strokeColor  = fill.copy(alpha = 0.9f),
                strokeWidth  = 2f,
            )
        }

        // ── Nearby driver markers ───────────────────────────────────────────────
        if (flowStep == CustomerFlowStep.HOME || flowStep == CustomerFlowStep.SEARCHING) {
            nearbyDrivers.forEach { driver ->
                val state = rememberMarkerState(
                    key      = driver.driverId,
                    position = driver.location.toGms(),
                )
                Marker(
                    state   = state,
                    icon    = driverIcon,
                    title   = driver.fullName,
                    snippet = "${driver.vehiclePlate ?: ""} · ${"%.1f".format(driver.rating)}★",
                )
            }
        }

        // ── Pickup marker ───────────────────────────────────────────────────────
        Marker(
            state   = rememberMarkerState(position = pickup.toGms()),
            icon    = pickupIcon,
            title   = "Điểm đón của bạn",
            zIndex  = 2f,
        )

        // ── Destination marker ──────────────────────────────────────────────────
        if (destination != null && flowStep >= CustomerFlowStep.TRIP_PREVIEW) {
            Marker(
                state   = rememberMarkerState(position = destination.toGms()),
                icon    = destIcon,
                title   = "Điểm đến",
                zIndex  = 2f,
            )
        }

        // ── Tracked driver animated marker ─────────────────────────────────────
        if (flowStep == CustomerFlowStep.TRACKING && trackedDriverLocation != null) {
            val trackedState = rememberMarkerState(position = trackedDriverLocation.toGms())
            LaunchedEffect(trackedDriverLocation) {
                trackedState.position = trackedDriverLocation.toGms()
            }
            Marker(
                state   = trackedState,
                icon    = trackedIcon,
                title   = "Tài xế của bạn",
                zIndex  = 3f,
            )
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

/** Create a 60×60 dp circular bitmap marker with an emoji label. */
private fun createCircleMarker(bgColor: Color, emoji: String): BitmapDescriptor {
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

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

/** Compute 6 vertices of a flat-top hexagon around a GMS center. */
private fun hexagonVertices(center: GmsLatLng, radiusDeg: Double): List<GmsLatLng> {
    val cosLat = cos(Math.toRadians(center.latitude))
    return (0 until 6).map { i ->
        val angleDeg = 60.0 * i - 30.0
        val angleRad = angleDeg * PI / 180.0
        GmsLatLng(
            center.latitude  + radiusDeg * cos(angleRad),
            center.longitude + radiusDeg * sin(angleRad) / cosLat,
        )
    }
}

// Helper: allow >= comparison on CustomerFlowStep ordinal
private operator fun CustomerFlowStep.compareTo(other: CustomerFlowStep) =
    this.ordinal.compareTo(other.ordinal)
