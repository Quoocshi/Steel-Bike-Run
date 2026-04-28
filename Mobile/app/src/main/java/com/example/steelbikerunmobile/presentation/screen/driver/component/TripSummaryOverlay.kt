package com.example.steelbikerunmobile.presentation.screen.driver.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.steelbikerunmobile.presentation.screen.driver.home.TripSummary
import kotlin.math.roundToLong

private val DriverOrange = Color(0xFFE67E22)
private val SummaryBg = Color(0xFF0F0F0F)
private val CardBg = Color(0xFF1C1C1E)

@Composable
fun TripSummaryOverlay(
    summary: TripSummary,
    totalTripsToday: Int,
    totalEarningsToday: Long,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Count-up animation for earnings
    val earningsAnim = remember { Animatable(0f) }
    LaunchedEffect(summary.earnings) {
        earningsAnim.animateTo(
            targetValue = summary.earnings.toFloat(),
            animationSpec = tween(durationMillis = 1400)
        )
    }
    val displayedEarnings = earningsAnim.value.roundToLong()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SummaryBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))

            // ── Celebration icon ───────────────────────────────────────────────
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(DriverOrange.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text("🎉", fontSize = 36.sp)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Hoàn thành chuyến đi!",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
                textAlign = TextAlign.Center
            )
            Text(
                "Tuyệt vời, bạn đã hoàn thành an toàn",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.45f)),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // ── Big animated earnings ──────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = CardBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Cuốc này",
                        style = MaterialTheme.typography.labelLarge.copy(
                            letterSpacing = 2.sp,
                            color = Color.White.copy(alpha = 0.45f)
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${displayedEarnings / 1000}K ₫",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = DriverOrange,
                        )
                    )
                    if (summary.surgeMultiplier > 1.0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "🔥 Surge ×${"%.1f".format(summary.surgeMultiplier)} đã áp dụng",
                            style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFFFFA726))
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Spacer(Modifier.height(16.dp))

                    // Trip stats
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        SummaryStatItem("📍", "${"%.1f".format(summary.distanceKm)} km", "Quãng đường")
                        SummaryStatItem("⏱", "${summary.durationMinutes} phút", "Thời gian")
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Session totals ─────────────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CardBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    SummaryStatItem("🏍", "$totalTripsToday cuốc", "Tổng hôm nay")
                    SummaryStatItem("💰", "${totalEarningsToday / 1000}K ₫", "Tổng doanh thu")
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Continue button ────────────────────────────────────────────────
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DriverOrange,
                    contentColor = Color.White
                )
            ) {
                Text(
                    "🏍  Tiếp tục nhận cuốc",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SummaryStatItem(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.45f)))
    }
}
