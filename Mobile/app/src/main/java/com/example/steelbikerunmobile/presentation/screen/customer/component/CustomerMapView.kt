package com.example.steelbikerunmobile.presentation.screen.customer.component

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
import com.example.steelbikerunmobile.domain.model.NearbyDriver
import com.example.steelbikerunmobile.presentation.screen.customer.home.CustomerFlowStep
import com.example.steelbikerunmobile.presentation.theme.CustomerPrimary
import com.example.steelbikerunmobile.presentation.theme.CustomerSecondary
import com.example.steelbikerunmobile.presentation.theme.DriverPrimary
import com.example.steelbikerunmobile.presentation.theme.ErrorRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
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

// ── Marker bitmap helpers (matching DriverMapView style) ──────────────────────
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
    var routePolyline by remember { mutableStateOf<Polyline?>(null) }
    val nearbyDriverMarkers = remember { mutableStateOf<List<Marker>>(emptyList()) }

    // Bitmap caches to avoid recreating on every recomposition
    val iconFactory by remember { mutableStateOf(IconFactory.getInstance(context)) }
    val pickupBmp = remember { createCircleMarkerBitmap("📍", CustomerPrimary) }
    val destBmp = remember { createCircleMarkerBitmap("🏁", ErrorRed) }
    val driverBmp = remember { createCircleMarkerBitmap("🚲", CustomerSecondary) }
    // Tracked driver uses motorcycle emoji like DriverMapView
    val trackedBmp = remember { createCircleMarkerBitmap("🏍", Color(0xFFE67E22), 112) }

    // Route fetching
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    val okHttpClient = remember { OkHttpClient() }

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
                // Center on driver when first entering TRACKING
                trackedDriverLocation?.toMapLibre() ?: pickup.toMapLibre()
            }
            else -> pickup.toMapLibre()
        }

        val zoom = if (flowStep == CustomerFlowStep.TRIP_PREVIEW) 13.5 else 15.0
        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(target, zoom),
        )
    }

    // ── Fetch route from driver to pickup (matching DriverMapView) ───────────
    LaunchedEffect(trackedDriverLocation, pickup, flowStep) {
        if (flowStep == CustomerFlowStep.TRACKING && trackedDriverLocation != null) {
            withContext(Dispatchers.IO) {
                try {
                    val url = "https://rsapi.goong.io/Direction?origin=${trackedDriverLocation.latitude},${trackedDriverLocation.longitude}&destination=${pickup.latitude},${pickup.longitude}&vehicle=bike&api_key=${BuildConfig.GOONG_API_KEY}"
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

    // ── Route polyline update ───────────────────────────────────────────────
    LaunchedEffect(routePoints, mapRef) {
        val map = mapRef ?: return@LaunchedEffect

        // Remove old route
        routePolyline?.remove()
        routePolyline = null

        // Add new route if available
        if (routePoints.isNotEmpty()) {
            routePolyline = map.addPolyline(
                PolylineOptions()
                    .addAll(routePoints)
                    .color(android.graphics.Color.parseColor("#4CAF50"))
                    .width(8f)
            )
        }
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
                    // First time — create marker with motorcycle emoji
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

// ── Polyline Decoder (matching DriverMapView) ─────────────────────────────────
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

