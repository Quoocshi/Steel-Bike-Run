package com.example.steelbikerunmobile.presentation.screen.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.steelbikerunmobile.domain.model.UserRole

@Composable
fun ProfileScreen(
    isDriverMode: Boolean,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val accentColor = if (isDriverMode) Color(0xFFE67E22) else Color(0xFF2ECC71)
    val bgColor = if (isDriverMode) Color(0xFF121212) else Color(0xFFF7FAF8)
    val surfaceColor = if (isDriverMode) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDriverMode) Color(0xFFE0E0E0) else Color(0xFF212529)
    val subtitleColor = if (isDriverMode) Color(0xFF9E9E9E) else Color(0xFF6C757D)
    val dividerColor = if (isDriverMode) Color(0xFF2C2C2C) else Color(0xFFE9ECEF)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Header gradient ───────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                accentColor,
                                accentColor.copy(alpha = 0.7f),
                                bgColor
                            )
                        )
                    )
            ) {
                // Back button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(start = 8.dp, top = 4.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại",
                        tint = Color.White
                    )
                }

                // Title
                Text(
                    text = "Hồ sơ cá nhân",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    ),
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(top = 12.dp)
                        .align(Alignment.TopCenter)
                )

                // Avatar
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(96.dp),
                        shape = CircleShape,
                        color = surfaceColor,
                        shadowElevation = 8.dp,
                        tonalElevation = 4.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val initials = uiState.userProfile?.fullName
                                ?.split(" ")
                                ?.mapNotNull { it.firstOrNull()?.uppercase() }
                                ?.takeLast(2)
                                ?.joinToString("")
                                ?: "?"

                            Text(
                                text = initials,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                )
                            )
                        }
                    }
                }
            }

            // ── Loading state ─────────────────────────────────────────────────
            AnimatedVisibility(
                visible = uiState.isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = accentColor)
                }
            }

            // ── Error state ───────────────────────────────────────────────────
            uiState.errorMessage?.let { error ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = viewModel::loadProfile) {
                        Text("Thử lại", color = accentColor)
                    }
                }
            }

            // ── Profile content ───────────────────────────────────────────────
            AnimatedVisibility(
                visible = !uiState.isLoading && uiState.userProfile != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val profile = uiState.userProfile ?: return@AnimatedVisibility

                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    // Name + role badge
                    Text(
                        text = profile.fullName,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        ),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(6.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accentColor.copy(alpha = 0.12f),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = when (profile.role) {
                                UserRole.DRIVER -> "🏍 Tài xế"
                                UserRole.CUSTOMER -> "👤 Khách hàng"
                                UserRole.ADMIN -> "🛡 Quản trị"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor
                            ),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    // ── Personal info card ────────────────────────────────────
                    ProfileSectionCard(
                        title = "Thông tin cá nhân",
                        surfaceColor = surfaceColor,
                        textColor = textColor,
                        subtitleColor = subtitleColor,
                        dividerColor = dividerColor,
                    ) {
                        ProfileInfoRow(
                            icon = Icons.Default.Person,
                            label = "Họ tên",
                            value = profile.fullName,
                            accentColor = accentColor,
                            textColor = textColor,
                            subtitleColor = subtitleColor,
                        )
                        HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                        ProfileInfoRow(
                            icon = Icons.Default.Email,
                            label = "Email",
                            value = profile.email,
                            accentColor = accentColor,
                            textColor = textColor,
                            subtitleColor = subtitleColor,
                        )
                        HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                        ProfileInfoRow(
                            icon = Icons.Default.Phone,
                            label = "Điện thoại",
                            value = profile.phone ?: "Chưa cập nhật",
                            accentColor = accentColor,
                            textColor = textColor,
                            subtitleColor = subtitleColor,
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── Driver info card (only when DRIVER) ──────────────────
                    val driverProfile = uiState.driverProfile
                    if (driverProfile != null) {
                        ProfileSectionCard(
                            title = "Thông tin tài xế",
                            surfaceColor = surfaceColor,
                            textColor = textColor,
                            subtitleColor = subtitleColor,
                            dividerColor = dividerColor,
                        ) {
                            ProfileInfoRow(
                                icon = Icons.Default.TwoWheeler,
                                label = "Biển số xe",
                                value = driverProfile.vehiclePlate ?: "—",
                                accentColor = accentColor,
                                textColor = textColor,
                                subtitleColor = subtitleColor,
                            )
                            HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                            ProfileInfoRow(
                                icon = Icons.Default.DirectionsBike,
                                label = "Xe",
                                value = listOfNotNull(
                                    driverProfile.vehicleColor,
                                    driverProfile.vehicleModel
                                ).joinToString(" ").ifBlank { "—" },
                                accentColor = accentColor,
                                textColor = textColor,
                                subtitleColor = subtitleColor,
                            )
                            HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                            ProfileInfoRow(
                                icon = Icons.Default.Star,
                                label = "Đánh giá",
                                value = "${"%.1f".format(driverProfile.rating)} ★  ·  ${driverProfile.totalTrips} cuốc",
                                accentColor = accentColor,
                                textColor = textColor,
                                subtitleColor = subtitleColor,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // ── Account info card ─────────────────────────────────────
                    ProfileSectionCard(
                        title = "Tài khoản",
                        surfaceColor = surfaceColor,
                        textColor = textColor,
                        subtitleColor = subtitleColor,
                        dividerColor = dividerColor,
                    ) {
                        ProfileInfoRow(
                            icon = Icons.Default.Person,
                            label = "ID",
                            value = profile.id.take(8) + "…",
                            accentColor = accentColor,
                            textColor = textColor,
                            subtitleColor = subtitleColor,
                        )
                        HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                        ProfileInfoRow(
                            icon = Icons.Default.Person,
                            label = "Trạng thái",
                            value = if (profile.isActive) "Đang hoạt động" else "Bị vô hiệu",
                            accentColor = accentColor,
                            textColor = if (profile.isActive) Color(0xFF2ECC71) else MaterialTheme.colorScheme.error,
                            subtitleColor = subtitleColor,
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    // ── Logout button ─────────────────────────────────────────
                    Button(
                        onClick = onLogout,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Đăng xuất",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

// ── Section card ──────────────────────────────────────────────────────────────
@Composable
private fun ProfileSectionCard(
    title: String,
    surfaceColor: Color,
    textColor: Color,
    subtitleColor: Color,
    dividerColor: Color,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = subtitleColor,
                    letterSpacing = 0.8.sp,
                ),
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// ── Info row ──────────────────────────────────────────────────────────────────
@Composable
private fun ProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color,
    textColor: Color,
    subtitleColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = subtitleColor,
                    letterSpacing = 0.5.sp
                )
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
            )
        }
    }
}
