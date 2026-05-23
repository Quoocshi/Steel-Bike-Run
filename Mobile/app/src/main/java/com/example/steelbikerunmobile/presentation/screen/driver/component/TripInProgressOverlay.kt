package com.example.steelbikerunmobile.presentation.screen.driver.component

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.steelbikerunmobile.presentation.screen.driver.home.ActiveTripData
import com.example.steelbikerunmobile.presentation.screen.driver.home.ARRIVED_THRESHOLD_METERS
import com.example.steelbikerunmobile.presentation.screen.driver.home.TripExecutionPhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val DriverOrange = Color(0xFFE67E22)
private val ActiveGreen = Color(0xFF2ECC71)
private val ArrivedBlue = Color(0xFF3498DB)
private val PanelDark = Color(0xFF1C1C1E)

@Composable
fun TripInProgressOverlay(
    activeTrip: ActiveTripData,
    onArrivedAtPickup: () -> Unit,
    onStartTrip: () -> Unit,
    onSwipeToComplete: () -> Unit,
    isLoading: Boolean = false,
    /** Khoảng cách (mét) đến điểm đón, null nếu chưa có GPS. */
    distanceToPickupMeters: Double? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Elapsed time ticker — chỉ tính khi đang IN_PROGRESS
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(activeTrip.tripId, activeTrip.executionPhase) {
        if (activeTrip.executionPhase == TripExecutionPhase.IN_PROGRESS) {
            while (true) {
                delay(1_000L)
                elapsedSeconds++
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = PanelDark,
        tonalElevation = 12.dp,
        shadowElevation = 24.dp,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {

            // Handle
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(40.dp, 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
            )
            Spacer(Modifier.height(16.dp))

            // ── Status banner (thay đổi theo phase) ───────────────────────────
            AnimatedContent(
                targetState = activeTrip.executionPhase,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label = "phase_banner"
            ) { phase ->
                val (bannerColor, bannerIcon, bannerText) = when (phase) {
                    TripExecutionPhase.GOING_TO_PICKUP -> Triple(
                        ActiveGreen.copy(alpha = 0.15f), "🏍", "Đang đến điểm đón khách"
                    )
                    TripExecutionPhase.ARRIVED_AT_PICKUP -> Triple(
                        ArrivedBlue.copy(alpha = 0.18f), "📍", "Đã đến điểm đón — Chờ khách lên xe"
                    )
                    TripExecutionPhase.IN_PROGRESS -> Triple(
                        DriverOrange.copy(alpha = 0.15f), "🚀", "Đang di chuyển đến điểm đến"
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(bannerColor, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(bannerIcon, fontSize = 18.sp)
                    Text(
                        bannerText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = when (phase) {
                                TripExecutionPhase.GOING_TO_PICKUP -> ActiveGreen
                                TripExecutionPhase.ARRIVED_AT_PICKUP -> ArrivedBlue
                                TripExecutionPhase.IN_PROGRESS -> DriverOrange
                            }
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Live stats row ─────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                if (activeTrip.executionPhase == TripExecutionPhase.IN_PROGRESS) {
                    TripStatItem("⏱", formatTime(elapsedSeconds), "Thời gian")
                } else {
                    TripStatItem("📍", "${activeTrip.totalDistanceKm.let { "%.1f".format(it) }} km", "Quãng đường")
                }
                TripStatItem("💰", "${activeTrip.estimatedEarnings / 1000}K ₫", "Doanh thu")
                TripStatItem(
                    "✖",
                    "x${"%.1f".format(activeTrip.surgeMultiplier)}",
                    "Surge"
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(Modifier.height(14.dp))

            // ── Customer info ─────────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(ArrivedBlue.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = ArrivedBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Khách hàng",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Text(
                        activeTrip.customerName,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
                androidx.compose.material3.IconButton(
                    onClick = {
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${activeTrip.customerPhone}"))
                        context.startActivity(dialIntent)
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Default.Call,
                        contentDescription = "Gọi khách hàng",
                        tint = ActiveGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Route info ─────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = ActiveGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        activeTrip.pickupAddress,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (activeTrip.executionPhase == TripExecutionPhase.IN_PROGRESS)
                                Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.8f)
                        ),
                        maxLines = 1
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = DriverOrange, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        activeTrip.destinationAddress,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (activeTrip.executionPhase == TripExecutionPhase.IN_PROGRESS)
                                Color.White else Color.White.copy(alpha = 0.5f)
                        ),
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            // ── CTA Button — thay đổi theo phase ──────────────────────────────
            AnimatedContent(
                targetState = activeTrip.executionPhase,
                transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(250)) },
                label = "cta_button"
            ) { phase ->
                if (isLoading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DriverOrange, modifier = Modifier.size(36.dp))
                    }
                } else {
                    when (phase) {
                        TripExecutionPhase.GOING_TO_PICKUP -> {
                            // Phase 1: Nút "Đã đến điểm đón" — enable chỉ khi ≤ ARRIVED_THRESHOLD_METERS
                            val canArrive = distanceToPickupMeters == null ||
                                    distanceToPickupMeters <= ARRIVED_THRESHOLD_METERS

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Chip hiển thị khoảng cách còn lại
                                if (distanceToPickupMeters != null) {
                                    val distText = if (distanceToPickupMeters < 1000)
                                        "${distanceToPickupMeters.toInt()}m"
                                    else "%.1fkm".format(distanceToPickupMeters / 1000)
                                    Row(
                                        Modifier
                                            .align(Alignment.CenterHorizontally)
                                            .background(
                                                if (canArrive) ActiveGreen.copy(alpha = 0.15f)
                                                else Color(0xFFE74C3C).copy(alpha = 0.15f),
                                                RoundedCornerShape(20.dp)
                                            )
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            if (canArrive) "✅" else "📏",
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            if (canArrive) "Đã đến nơi"
                                            else "Còn $distText nữa",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (canArrive) ActiveGreen
                                                else Color(0xFFE74C3C),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                    }
                                }

                                Button(
                                    onClick = onArrivedAtPickup,
                                    enabled = canArrive,
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (canArrive) ActiveGreen
                                        else Color(0xFF555555),
                                        disabledContainerColor = Color(0xFF3A3A3A),
                                    ),
                                ) {
                                    Text(
                                        if (canArrive) "📍  Đã đến điểm đón"
                                        else "📍  Tiến đến điểm đón",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (canArrive) Color.White
                                            else Color.White.copy(alpha = 0.4f)
                                        )
                                    )
                                }
                            }
                        }
                        TripExecutionPhase.ARRIVED_AT_PICKUP -> {
                            // Phase 2: Nút "Bắt đầu chuyến đi" — màu xanh dương
                            Button(
                                onClick = onStartTrip,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ArrivedBlue),
                            ) {
                                Text(
                                    "🏍  Bắt đầu chuyến đi",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                            }
                        }
                        TripExecutionPhase.IN_PROGRESS -> {
                            // Phase 3: Swipe to complete — màu cam
                            SwipeToCompleteButton(onComplete = onSwipeToComplete)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

// ── Swipe to Complete ─────────────────────────────────────────────────────────
@Composable
private fun SwipeToCompleteButton(onComplete: () -> Unit) {
    val thumbDp = 60.dp
    val density = LocalDensity.current
    val thumbPx = with(density) { thumbDp.toPx() }
    val scope = rememberCoroutineScope()
    val thumbOffset = remember { Animatable(0f) }
    var trackWidth by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(thumbDp)
            .clip(RoundedCornerShape(30.dp))
            .background(DriverOrange.copy(alpha = 0.18f))
    ) {
        trackWidth = with(density) { maxWidth.toPx() }
        val maxOffset = trackWidth - thumbPx

        // Progress fill
        Box(
            Modifier
                .fillMaxWidth((thumbOffset.value + thumbPx) / trackWidth.coerceAtLeast(1f))
                .height(thumbDp)
                .clip(RoundedCornerShape(30.dp))
                .background(DriverOrange.copy(alpha = 0.32f))
        )

        // Label
        Box(Modifier.fillMaxWidth().height(thumbDp), contentAlignment = Alignment.Center) {
            Text(
                "HOÀN THÀNH  →",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = Color.White.copy(
                        alpha = (1f - thumbOffset.value / maxOffset.coerceAtLeast(1f)) * 0.65f + 0.15f
                    )
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
                                    onComplete()
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
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.ArrowForward, contentDescription = "Hoàn thành", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

// ── Small helpers ─────────────────────────────────────────────────────────────
@Composable
private fun TripStatItem(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.45f)))
    }
}

private fun formatTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}
