package com.example.steelbikerunmobile.presentation.screen.customer.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.steelbikerunmobile.presentation.component.atom.SbTextButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FindingDriverOverlay(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 3 staggered radar pulse waves
    val wave1 = remember { Animatable(0f) }
    val wave2 = remember { Animatable(0f) }
    val wave3 = remember { Animatable(0f) }

    val waveDuration = 2_200

    LaunchedEffect(Unit) {
        launch {
            while (true) {
                wave1.snapTo(0f)
                wave1.animateTo(1f, tween(waveDuration, easing = LinearEasing))
            }
        }
        launch {
            delay((waveDuration / 3).toLong())
            while (true) {
                wave2.snapTo(0f)
                wave2.animateTo(1f, tween(waveDuration, easing = LinearEasing))
            }
        }
        launch {
            delay((waveDuration / 3 * 2).toLong())
            while (true) {
                wave3.snapTo(0f)
                wave3.animateTo(1f, tween(waveDuration, easing = LinearEasing))
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // ── Radar canvas ────────────────────────────────────────────────────
            val radarColor = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier.size(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val maxRadius = size.minDimension / 2f
                    listOf(wave1.value, wave2.value, wave3.value).forEach { progress ->
                        val radius = maxRadius * progress
                        val alpha  = (1f - progress) * 0.7f
                        drawCircle(
                            color  = radarColor.copy(alpha = alpha),
                            radius = radius,
                            style  = Stroke(width = 3.5.dp.toPx()),
                        )
                    }
                }

                // Center glow + bike emoji
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "\uD83D\uDEB2", fontSize = 28.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Đang tìm tài xế gần nhất...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Vui lòng đợi trong giây lát",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            SbTextButton(
                text = "Hủy tìm kiếm",
                onClick = onCancel,
                modifier = Modifier,
            )
        }
    }
}
