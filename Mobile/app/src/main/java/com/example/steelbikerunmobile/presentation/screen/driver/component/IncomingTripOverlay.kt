package com.example.steelbikerunmobile.presentation.screen.driver.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.steelbikerunmobile.presentation.screen.driver.home.IncomingTripData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val DriverOrange = Color(0xFFE67E22)
private val DriverOrangeDark = Color(0xFFD35400)
private val OverlayBg = Color(0xCC0A0A0A)

// ── Entry point ───────────────────────────────────────────────────────────────
@Composable
fun IncomingTripOverlay(
    tripData: IncomingTripData,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    var secondsLeft by remember { mutableIntStateOf(tripData.countdownSeconds) }

    // Countdown timer
    LaunchedEffect(tripData.tripId) {
        while (secondsLeft > 0) {
            delay(1_000L)
            secondsLeft--
        }
        onDecline()
    }

    // Background pulse flash
    val infiniteTransition = rememberInfiniteTransition(label = "ring")
    val flashAlpha by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 0.18f,
        animationSpec = infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "flash"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OverlayBg),
        contentAlignment = Alignment.Center
    ) {
        // Pulsing background tint
        Box(
            Modifier
                .fillMaxSize()
                .background(DriverOrange.copy(alpha = flashAlpha))
        )

        // Pulsing rings behind the card
        PulsingRings()

        // The trip card
        TripCard(
            tripData = tripData,
            secondsLeft = secondsLeft,
            onAccept = onAccept,
            onDecline = onDecline
        )
    }
}

// ── Pulsing concentric rings ──────────────────────────────────────────────────
@Composable
private fun PulsingRings() {
    val scale1 = remember { Animatable(0.5f) }
    val alpha1 = remember { Animatable(0.5f) }
    val scale2 = remember { Animatable(0.5f) }
    val alpha2 = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            while (true) {
                scale1.snapTo(0.5f); alpha1.snapTo(0.5f)
                launch { scale1.animateTo(1.4f, tween(1200, easing = LinearEasing)) }
                alpha1.animateTo(0f, tween(1200))
            }
        }
        launch {
            delay(600)
            while (true) {
                scale2.snapTo(0.5f); alpha2.snapTo(0.5f)
                launch { scale2.animateTo(1.4f, tween(1200, easing = LinearEasing)) }
                alpha2.animateTo(0f, tween(1200))
            }
        }
    }

    Canvas(modifier = Modifier.size(320.dp)) {
        val cx = size.width / 2; val cy = size.height / 2
        val maxR = size.minDimension / 2
        drawCircle(DriverOrange.copy(alpha = alpha1.value), maxR * scale1.value, Offset(cx, cy), style = Stroke(3.dp.toPx()))
        drawCircle(DriverOrange.copy(alpha = alpha2.value), maxR * scale2.value, Offset(cx, cy), style = Stroke(3.dp.toPx()))
    }
}

// ── Trip card ─────────────────────────────────────────────────────────────────
@Composable
private fun TripCard(
    tripData: IncomingTripData,
    secondsLeft: Int,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF1C1C1E),
        tonalElevation = 16.dp,
        shadowElevation = 24.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {

            // Header row: icon + title + countdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔔", fontSize = 26.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Cuốc mới!",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        if (tripData.surgeMultiplier > 1.0) {
                            Text(
                                "🔥 Surge ×${tripData.surgeMultiplier}",
                                style = MaterialTheme.typography.labelMedium.copy(color = DriverOrange)
                            )
                        }
                    }
                }
                CountdownArc(seconds = secondsLeft, total = tripData.countdownSeconds)
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(Modifier.height(16.dp))

            // Earnings + distance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                MetricPill(label = "Doanh thu ước tính", value = "${tripData.estimatedEarnings / 1000}K ₫", icon = "💰")
                val distanceFmt = "%.1f".format(tripData.distanceToPickupKm)
                MetricPill(label = "Đến điểm đón", value = "$distanceFmt km", icon = "📍")
                MetricPill(label = "Chuyến đi", value = "${tripData.durationMinutes} phút", icon = "⏱")
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(Modifier.height(16.dp))

            // Route
            RouteInfo(pickup = tripData.pickupAddress, destination = tripData.destinationAddress)

            // Customer info
            if (tripData.customerName.isNotBlank() || tripData.customerPhone.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(Modifier.height(14.dp))
                CustomerInfoRow(name = tripData.customerName, phone = tripData.customerPhone)
            }

            Spacer(Modifier.height(28.dp))

            // Swipe-to-accept
            SwipeToAcceptButton(onAccept = onAccept)

            Spacer(Modifier.height(8.dp))

            // Decline
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = onDecline) {
                    Text(
                        "Bỏ qua",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.45f)
                        )
                    )
                }
            }
        }
    }
}

// ── Countdown arc ─────────────────────────────────────────────────────────────
@Composable
private fun CountdownArc(seconds: Int, total: Int) {
    val progress = seconds.toFloat() / total.toFloat()
    val arcColor = when {
        progress > 0.5f -> DriverOrange
        progress > 0.25f -> Color(0xFFFFA726)
        else -> Color(0xFFEF5350)
    }
    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(5.dp.toPx(), cap = StrokeCap.Round)
            drawArc(Color.White.copy(alpha = 0.12f), 0f, 360f, false, style = stroke)
            drawArc(arcColor, -90f, progress * 360f, false, style = stroke)
        }
        Text(
            "$seconds",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = arcColor
            )
        )
    }
}

// ── Metric pill ───────────────────────────────────────────────────────────────
@Composable
private fun MetricPill(label: String, value: String, icon: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 20.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.45f)),
            maxLines = 1
        )
    }
}

// ── Route info ────────────────────────────────────────────────────────────────
@Composable
private fun RouteInfo(pickup: String, destination: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(pickup, style = MaterialTheme.typography.bodyMedium.copy(color = Color.White), maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Star, contentDescription = null, tint = DriverOrange, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(destination, style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.8f)), maxLines = 1)
        }
    }
}

// ── Customer info row ─────────────────────────────────────────────────────────
@Composable
private fun CustomerInfoRow(name: String, phone: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(DriverOrange.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (name.isNotBlank()) name.first().uppercaseChar().toString() else "?",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = DriverOrange
                )
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (name.isNotBlank()) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                )
            }
            if (phone.isNotBlank()) {
                Text(
                    text = "📞  $phone",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.6f)
                    )
                )
            }
        }
    }
}

// ── Swipe-to-accept ───────────────────────────────────────────────────────────
@Composable
private fun SwipeToAcceptButton(onAccept: () -> Unit) {
    val thumbDp = 56.dp
    val density = LocalDensity.current
    val thumbPx = with(density) { thumbDp.toPx() }
    val scope = rememberCoroutineScope()

    val thumbOffset = remember { Animatable(0f) }
    var trackWidth by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(thumbDp)
            .clip(CircleShape)
            .background(DriverOrange.copy(alpha = 0.15f))
    ) {
        trackWidth = with(density) { maxWidth.toPx() }
        val maxOffset = trackWidth - thumbPx

        // Fill bar
        Box(
            Modifier
                .fillMaxWidth((thumbOffset.value + thumbPx) / trackWidth)
                .height(thumbDp)
                .background(DriverOrange.copy(alpha = 0.30f), CircleShape)
        )

        // Hint label
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "CHẤP NHẬN  →",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = Color.White.copy(alpha = (1f - thumbOffset.value / maxOffset.coerceAtLeast(1f)) * 0.6f + 0.1f)
                )
            )
        }

        // Draggable thumb
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.value.roundToInt(), 0) }
                .size(thumbDp)
                .clip(CircleShape)
                .background(DriverOrange)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val progress = if (maxOffset > 0) thumbOffset.value / maxOffset else 0f
                            scope.launch {
                                if (progress >= 0.80f) {
                                    thumbOffset.animateTo(maxOffset, tween(120))
                                    delay(80)
                                    onAccept()
                                } else {
                                    thumbOffset.animateTo(0f, spring(dampingRatio = 0.6f))
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                thumbOffset.snapTo((thumbOffset.value + dragAmount).coerceIn(0f, maxOffset))
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.ArrowForward,
                contentDescription = "Chấp nhận",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
