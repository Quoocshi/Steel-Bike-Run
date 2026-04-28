package com.example.steelbikerunmobile.presentation.screen.driver.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.example.steelbikerunmobile.domain.model.LatLng as DomainLatLng
import com.example.steelbikerunmobile.domain.model.SurgeZone
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

// ── Coordinate helpers ────────────────────────────────────────────────────────
private fun DomainLatLng.toGms() = LatLng(latitude, longitude)

/** Six vertices of a flat-top hexagon centred at [center] with given [radiusDeg]. */
private fun hexagonVertices(center: LatLng, radiusDeg: Double): List<LatLng> =
    (0..5).map { i ->
        val angleDeg = 60.0 * i - 30.0
        val rad = Math.toRadians(angleDeg)
        LatLng(
            center.latitude + radiusDeg * cos(rad),
            center.longitude + radiusDeg * sin(rad) / cos(Math.toRadians(center.latitude))
        )
    }

/** Surge-level → semi-transparent red/orange fill */
private fun surgeColor(multiplier: Double): Color {
    val alpha = (0.25f + ((multiplier - 1.0) * 0.15f).toFloat()).coerceIn(0.25f, 0.60f)
    return if (multiplier >= 1.8) Color(1f, 0.2f, 0.05f, alpha) // red
    else Color(0.90f, 0.45f, 0.05f, alpha)                       // orange
}

// ── Marker bitmap helpers ─────────────────────────────────────────────────────
private fun createCircleMarker(emoji: String, bgColor: Color, sizePx: Int = 96): BitmapDescriptor {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = bgColor.toArgb()
    val r = sizePx / 2f
    canvas.drawCircle(r, r, r, paint)
    paint.color = android.graphics.Color.WHITE
    paint.alpha = 80
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 4f
    canvas.drawCircle(r, r, r - 2f, paint)
    paint.reset()
    paint.isAntiAlias = true
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = sizePx * 0.46f
    paint.typeface = Typeface.DEFAULT_BOLD
    canvas.drawText(emoji, r, r - (paint.ascent() + paint.descent()) / 2f, paint)
    return BitmapDescriptorFactory.fromBitmap(bmp)
}

// ── DriverMapView ─────────────────────────────────────────────────────────────
@Composable
fun DriverMapView(
    driverLocation: DomainLatLng?,
    surgeZones: List<SurgeZone>,
    modifier: Modifier = Modifier
) {
    val cameraState = rememberCameraPositionState()
    val defaultCenter = LatLng(10.7769, 106.7009)

    // Animate camera to driver location
    LaunchedEffect(driverLocation) {
        val target = driverLocation?.toGms() ?: defaultCenter
        cameraState.animate(CameraUpdateFactory.newLatLngZoom(target, 14f))
    }

    val driverIcon = remember {
        createCircleMarker("🏍", Color(0xFFE67E22), sizePx = 112)
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraState,
        properties = MapProperties(
            mapType = MapType.HYBRID,   // Satellite + roads = naturally dark, high-contrast
            isMyLocationEnabled = false
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = false
        )
    ) {
        // Surge zone hexagons
        surgeZones.forEach { zone ->
            val center = LatLng(zone.center.latitude, zone.center.longitude)
            val vertices = hexagonVertices(center, 0.0018)
            val fill = surgeColor(zone.surgeMultiplier)
            val stroke = if (zone.surgeMultiplier >= 1.8) Color(1f, 0.2f, 0.05f, 0.85f)
            else Color(0.90f, 0.45f, 0.05f, 0.85f)

            Polygon(
                points = vertices,
                fillColor = fill,
                strokeColor = stroke,
                strokeWidth = 3f,
                zIndex = 1f
            )
        }

        // Driver's own location marker
        driverLocation?.let { loc ->
            Marker(
                state = rememberMarkerState(position = loc.toGms()),
                icon = driverIcon,
                title = "Vị trí của bạn",
                zIndex = 10f
            )
        }
    }
}
