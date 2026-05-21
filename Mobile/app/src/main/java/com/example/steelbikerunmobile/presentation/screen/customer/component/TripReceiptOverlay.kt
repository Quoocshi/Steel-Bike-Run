package com.example.steelbikerunmobile.presentation.screen.customer.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.steelbikerunmobile.presentation.screen.customer.home.TripReceipt

private val CustomerGreen = Color(0xFF2ECC71)
private val GoldStar = Color(0xFFF39C12)
private val ReceiptBg = Color(0xFFF7FAF8)

@Composable
fun TripReceiptOverlay(
    receipt: TripReceipt,
    onRatingChanged: (Int) -> Unit,
    onCommentChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ReceiptBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            // ── Animated checkmark ─────────────────────────────────────────────
            val checkScale = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                checkScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f))
            }
            Box(
                Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(CustomerGreen)
                    .scale(checkScale.value),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", fontSize = 38.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Chuyến đi hoàn thành!",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF1A1A1A)
            )
            Text(
                "Cảm ơn bạn đã đi cùng Steel Bike Run",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6C757D),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            // ── Fare card ──────────────────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Route
                    RouteRow(
                        pickup = receipt.pickupAddress,
                        destination = receipt.destinationAddress
                    )
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFFEEEEEE))
                    Spacer(Modifier.height(14.dp))

                    // Stats grid
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        ReceiptStat("📍", "${"%.1f".format(receipt.distanceKm)} km", "Quãng đường")
                        ReceiptStat("⏱", "${receipt.durationMinutes} phút", "Thời gian")
                        if (receipt.surgeMultiplier > 1.0) {
                            ReceiptStat("🔥", "×${"%.1f".format(receipt.surgeMultiplier)}", "Surge")
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFFEEEEEE))
                    Spacer(Modifier.height(14.dp))

                    // Fare breakdown
                    FareRow("Cước cơ bản", "${receipt.baseFare / 1000}K ₫")
                    if (receipt.surgeMultiplier > 1.0) {
                        val surgeFee = receipt.totalFare - receipt.baseFare
                        FareRow("Phụ thu giờ cao điểm", "+${surgeFee / 1000}K ₫", Color(0xFFE67E22))
                    }
                    Spacer(Modifier.height(8.dp))

                    // Total – large
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Tổng cộng",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "${receipt.totalFare / 1000}K ₫",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = CustomerGreen
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Rating section ─────────────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Bạn thấy chuyến đi thế nào?",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(Modifier.height(12.dp))

                    // 5-star selector
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..5).forEach { star ->
                            IconButton(
                                onClick = { onRatingChanged(star) },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "$star sao",
                                    tint = if (star <= receipt.rating) GoldStar else Color(0xFFCCCCCC),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }

                    if (receipt.rating > 0) {
                        Text(
                            text = when (receipt.rating) {
                                1 -> "Rất tệ 😞"
                                2 -> "Tệ 😕"
                                3 -> "Bình thường 😐"
                                4 -> "Tốt 😊"
                                else -> "Tuyệt vời! 🌟"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (receipt.rating >= 4) CustomerGreen else Color(0xFF6C757D)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Comment field
                    OutlinedTextField(
                        value = receipt.comment,
                        onValueChange = onCommentChanged,
                        placeholder = { Text("Nhận xét về chuyến đi (tuỳ chọn)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 3,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Submit button ──────────────────────────────────────────────────
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = CustomerGreen,
                )
            } else {
                Button(
                    onClick = onSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CustomerGreen,
                        contentColor = Color.White
                    ),
                    enabled = receipt.rating > 0
                ) {
                    Text(
                        if (receipt.rating > 0) "Gửi đánh giá" else "Bỏ qua đánh giá",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // ── Skip / Go back button ─────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF6C757D)
                )
            ) {
                Text(
                    "Bỏ qua",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Small helpers ─────────────────────────────────────────────────────────────

@Composable
private fun RouteRow(pickup: String, destination: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(CustomerGreen))
            Spacer(Modifier.size(10.dp))
            Text(pickup, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFE74C3C)))
            Spacer(Modifier.size(10.dp))
            Text(destination, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
    }
}

@Composable
private fun ReceiptStat(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF6C757D)))
    }
}

@Composable
private fun FareRow(label: String, value: String, valueColor: Color = Color(0xFF1A1A1A)) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF6C757D))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = valueColor))
    }
}
