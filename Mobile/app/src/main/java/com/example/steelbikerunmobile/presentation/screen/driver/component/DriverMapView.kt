package com.example.steelbikerunmobile.presentation.screen.driver.component

import android.graphics.Bitmap
import android.graphics.Canvas
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
import com.example.steelbikerunmobile.presentation.screen.driver.home.TripExecutionPhase
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.annotations.PolylineOptions
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.cos
import kotlin.math.sin

// ── Goong dark style for Driver mode ───────────────────────────────────────
private val STYLE_URL: String
    get() = "https://tiles.goong.io/assets/goong_map_dark.json?api_key=${BuildConfig.GOONG_MAP_KEY}"

// ── Coordinate helpers ────────────────────────────────────────────────────────
private fun DomainLatLng.toMapLibre() = LatLng(latitude, longitude)

// ── Marker bitmap helpers ─────────────────────────────────────────────────────
private fun createCircleMarkerBitmap(emoji: String, bgColor: Color, sizePx: Int = 96): Bitmap {
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
    return bmp
}

// ── DriverMapView ─────────────────────────────────────────────────────────────
@Composable
fun DriverMapView(
    driverLocation: DomainLatLng?,
    pickupLocation: DomainLatLng?,
    destinationLocation: DomainLatLng?,
    executionPhase: TripExecutionPhase,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val defaultCenter = LatLng(10.7769, 106.7009) // HCMC

    // Initialise MapLibre once per process
    remember { MapLibre.getInstance(context) }

    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }

    val mapView = remember {
        MapView(context).apply {
            getMapAsync { mlMap ->
                mlMap.setStyle(Style.Builder().fromUri(STYLE_URL)) {
                    mlMap.uiSettings.apply {
                        isCompassEnabled = false
                    }
                    val target = driverLocation?.toMapLibre() ?: defaultCenter
                    mlMap.cameraPosition = CameraPosition.Builder()
                        .target(target)
                        .zoom(14.0)
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
    LaunchedEffect(driverLocation, mapRef) {
        val map = mapRef ?: return@LaunchedEffect
        val target = driverLocation?.toMapLibre() ?: defaultCenter
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 14.0))
    }

    // ── Route: pickup vs destination based on phase ─────────────────────────
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    val okHttpClient = remember { OkHttpClient() }

    val routeDestination: DomainLatLng? = when (executionPhase) {
        TripExecutionPhase.GOING_TO_PICKUP -> pickupLocation
        TripExecutionPhase.ARRIVED_AT_PICKUP -> null
        TripExecutionPhase.IN_PROGRESS -> destinationLocation
    }

    LaunchedEffect(driverLocation, routeDestination) {
        if (driverLocation != null && routeDestination != null) {
            withContext(Dispatchers.IO) {
                try {
                    val url = "https://rsapi.goong.io/Direction?origin=${driverLocation.latitude},${driverLocation.longitude}&destination=${routeDestination.latitude},${routeDestination.longitude}&vehicle=bike&api_key=${BuildConfig.GOONG_API_KEY}"
                    val request = Request.Builder().url(url).build()
                    val response = okHttpClient.newCall(request).execute()
                    val body = response.body?.string()
                    if (response.isSuccessful && body != null) {
                        val json = JSONObject(body)
                        val routes = json.optJSONArray("routes")
                        if (routes != null && routes.length() > 0) {
                            val encoded = routes.getJSONObject(0).getJSONObject("overview_polyline").getString("points")
                            routePoints = decodePolyline(encoded)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            routePoints = emptyList()
        }
    }

    // ── Markers & overlays ──────────────────────────────────────────────────
    LaunchedEffect(driverLocation, pickupLocation, destinationLocation, routePoints, mapRef) {
        val map = mapRef ?: return@LaunchedEffect
        map.clear()

        val iconFactory = IconFactory.getInstance(context)
        val driverBmp = createCircleMarkerBitmap("🏍", Color(0xFFE67E22), sizePx = 112)

        // Driver's own location marker
        driverLocation?.let { loc ->
            map.addMarker(
                MarkerOptions()
                    .position(loc.toMapLibre())
                    .icon(iconFactory.fromBitmap(driverBmp))
                    .title("Vị trí của bạn")
            )
        }

        // Add route (green for pickup phase, blue for destination phase)
        if (routePoints.isNotEmpty()) {
            val routeColor = when (executionPhase) {
                TripExecutionPhase.GOING_TO_PICKUP -> "#4CAF50"
                TripExecutionPhase.ARRIVED_AT_PICKUP -> "#4CAF50"
                TripExecutionPhase.IN_PROGRESS -> "#2196F3"
            }
            map.addPolyline(
                PolylineOptions()
                    .addAll(routePoints)
                    .color(android.graphics.Color.parseColor(routeColor))
                    .width(8f)
            )
        }

        // Add pickup marker (only shown before IN_PROGRESS)
        if (executionPhase != TripExecutionPhase.IN_PROGRESS) {
            pickupLocation?.let { loc ->
                val pickupBmp = createCircleMarkerBitmap("📍", Color(0xFFE53935), sizePx = 112)
                map.addMarker(
                    MarkerOptions()
                        .position(loc.toMapLibre())
                        .icon(iconFactory.fromBitmap(pickupBmp))
                        .title("Vị trí khách hàng")
                )
            }
        }

        // Add destination marker (only shown in IN_PROGRESS)
        if (executionPhase == TripExecutionPhase.IN_PROGRESS) {
            destinationLocation?.let { loc ->
                val destBmp = createCircleMarkerBitmap("🏁", Color(0xFFE53935), sizePx = 112)
                map.addMarker(
                    MarkerOptions()
                        .position(loc.toMapLibre())
                        .icon(iconFactory.fromBitmap(destBmp))
                        .title("Điểm đến")
                )
            }
        }
    }

    // ── Render MapView ──────────────────────────────────────────────────────
    AndroidView(
        factory = { mapView },
        modifier = modifier,
    )
}

// ── Polyline Decoder ──────────────────────────────────────────────────────────
private fun decodePolyline(encoded: String): List<LatLng> {
    val poly = ArrayList<LatLng>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0

    while (index < len) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            b = encoded[index++].code - 63
            result = result or (b and 0x1f shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lat += dlat

        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or (b and 0x1f shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lng += dlng

        val p = LatLng(
            lat.toDouble() / 1E5,
            lng.toDouble() / 1E5
        )
        poly.add(p)
    }
    return poly
}
