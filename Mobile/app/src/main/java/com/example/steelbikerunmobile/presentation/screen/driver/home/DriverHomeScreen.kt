package com.example.steelbikerunmobile.presentation.screen.driver.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.steelbikerunmobile.presentation.screen.driver.component.DriverMapView
import com.example.steelbikerunmobile.presentation.screen.driver.component.FaceScanOverlay
import com.example.steelbikerunmobile.presentation.screen.driver.component.IncomingTripOverlay
import com.example.steelbikerunmobile.presentation.screen.driver.component.TripInProgressOverlay
import com.example.steelbikerunmobile.presentation.screen.driver.component.TripSummaryOverlay

private val DriverOrange = Color(0xFFE67E22)
private val DriverOrangeDark = Color(0xFFD35400)
private val PanelBg = Color(0xFF1C1C1E)
private val OnlineGreen = Color(0xFF2ECC71)

// ── Entry point ───────────────────────────────────────────────────────────────
@Composable
fun DriverHomeScreen(
    onLogout: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    viewModel: DriverHomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) viewModel.startLocationStream()
    }
    val hasLocationPermission = {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    val onToggle: () -> Unit = {
        if (!hasLocationPermission()) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
        viewModel.onToggleOnlineClicked(hasLocationPermission())
    }

    // Khi profile được load và driver đang online nhưng stream chưa chạy
    // (vd: vừa switch từ Customer→Driver, permission đã có sẵn),
    // thì tự động khởi động GPS stream.
    val isOnline = uiState.profile?.isOnline == true
    LaunchedEffect(isOnline) {
        if (isOnline && !uiState.isStreamingLocation && hasLocationPermission()) {
            viewModel.startLocationStream()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // Determine if we need to show route to customer
        val pickupLocation = uiState.activeTrip?.let { com.example.steelbikerunmobile.domain.model.LatLng(it.pickupLat, it.pickupLng) }
            ?: uiState.incomingTrip?.let { com.example.steelbikerunmobile.domain.model.LatLng(it.pickupLat, it.pickupLng) }

        // ── Layer 1: Full-screen map ──────────────────────────────────────────
        DriverMapView(
            driverLocation = uiState.currentLocation,
            pickupLocation = pickupLocation,
            surgeZones = uiState.surgeZones,
            modifier = Modifier.fillMaxSize()
        )

        // ── Layer 2: Top bar (floating) ───────────────────────────────────────
        DriverTopBar(
            isOnline = uiState.profile?.isOnline == true,
            onSwitchToCustomer = viewModel::switchBackToCustomer,
            isSwitchingRole = uiState.isLoading,
            onProfileClicked = onNavigateToProfile,
            onLogout = onLogout,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // ── Layer 3: Bottom panel ─────────────────────────────────────────────
        DriverBottomPanel(
            uiState = uiState,
            onToggle = onToggle,
            onVehiclePlateChange = viewModel::onVehiclePlateChange,
            onVehicleModelChange = viewModel::onVehicleModelChange,
            onVehicleColorChange = viewModel::onVehicleColorChange,
            onLicenseNumberChange = viewModel::onLicenseNumberChange,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )

        // ── Layer 4: Face Scan overlay ────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.step == DriverHomeStep.FACE_SCAN,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300))
        ) {
            FaceScanOverlay(
                onPass = { viewModel.onFaceScanPassed(hasLocationPermission()) },
                onFail = viewModel::onFaceScanFailed
            )
        }

        // ── Layer 5: Incoming trip overlay ────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.step == DriverHomeStep.INCOMING_TRIP && uiState.incomingTrip != null,
            enter = slideInVertically(tween(400)) { it } + fadeIn(tween(400)),
            exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(300))
        ) {
            uiState.incomingTrip?.let { trip ->
                IncomingTripOverlay(
                    tripData = trip,
                    onAccept = viewModel::onTripAccepted,
                    onDecline = viewModel::onTripDeclined
                )
            }
        }

        // ── Layer 6: Trip in-progress panel ───────────────────────────────────
        AnimatedVisibility(
            visible = uiState.step == DriverHomeStep.TRIP_IN_PROGRESS && uiState.activeTrip != null,
            enter = slideInVertically(tween(400)) { it } + fadeIn(tween(400)),
            exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            uiState.activeTrip?.let { trip ->
                TripInProgressOverlay(
                    activeTrip = trip,
                    onArrivedAtPickup = viewModel::onArrivedAtPickup,
                    onStartTrip = viewModel::onStartTrip,
                    onSwipeToComplete = viewModel::onSwipeToComplete,
                    isLoading = uiState.isLoading,
                    distanceToPickupMeters = uiState.distanceToPickupMeters,
                )
            }
        }

        // ── Layer 7: Trip summary (full-screen) ───────────────────────────────
        AnimatedVisibility(
            visible = uiState.step == DriverHomeStep.TRIP_SUMMARY && uiState.tripSummary != null,
            enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 2 },
            exit = fadeOut(tween(250))
        ) {
            uiState.tripSummary?.let { summary ->
                TripSummaryOverlay(
                    summary = summary,
                    totalTripsToday = uiState.todayTrips,
                    totalEarningsToday = uiState.todayEarnings,
                    onContinue = viewModel::onSummaryDismissed
                )
            }
        }
    }
}

// ── Top floating bar ──────────────────────────────────────────────────────────
@Composable
private fun DriverTopBar(
    isOnline: Boolean,
    onSwitchToCustomer: () -> Unit,
    isSwitchingRole: Boolean,
    onProfileClicked: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status badge
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = PanelBg.copy(alpha = 0.92f),
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) OnlineGreen else Color.Gray)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isOnline) "ĐANG ONLINE" else "ĐANG OFFLINE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = if (isOnline) OnlineGreen else Color.White.copy(alpha = 0.55f)
                    )
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Switch-back-to-Customer button
            Surface(
                shape = CircleShape,
                color = PanelBg.copy(alpha = 0.88f)
            ) {
                IconButton(
                    onClick = onSwitchToCustomer,
                    enabled = !isSwitchingRole,
                ) {
                    Icon(
                        Icons.Outlined.PersonOutline,
                        contentDescription = "Về chế độ Khách hàng",
                        tint = Color.White.copy(alpha = if (isSwitchingRole) 0.35f else 0.85f)
                    )
                }
            }

            // Profile button
            Surface(
                shape = CircleShape,
                color = PanelBg.copy(alpha = 0.88f)
            ) {
                IconButton(onClick = onProfileClicked) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Hồ sơ cá nhân",
                        tint = DriverOrange.copy(alpha = 0.85f)
                    )
                }
            }

            // Emergency logout button
            Surface(
                shape = CircleShape,
                color = Color(0xFF3D1515).copy(alpha = 0.88f)
            ) {
                IconButton(onClick = onLogout) {
                    Icon(
                        Icons.Default.ExitToApp,
                        contentDescription = "Đăng xuất",
                        tint = Color(0xFFEF5350)
                    )
                }
            }
        }
    }
}

// ── Bottom panel ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriverBottomPanel(
    uiState: DriverHomeUiState,
    onToggle: () -> Unit,
    onVehiclePlateChange: (String) -> Unit,
    onVehicleModelChange: (String) -> Unit,
    onVehicleColorChange: (String) -> Unit,
    onLicenseNumberChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isOnline = uiState.profile?.isOnline == true
    var showVehicleSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = PanelBg,
        tonalElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {

            // Handle indicator
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(40.dp, 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
            )
            Spacer(Modifier.height(16.dp))

            // Stats row
            AnimatedContent(
                targetState = isOnline,
                transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) },
                label = "stats"
            ) { online ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    StatChip(
                        emoji = "💰",
                        label = "Hôm nay",
                        value = if (online) "${uiState.todayEarnings / 1000}K ₫" else "--"
                    )
                    StatChip(
                        emoji = "🏍",
                        label = "Cuốc",
                        value = if (online) "${uiState.todayTrips}" else "--"
                    )
                    StatChip(
                        emoji = if (online) "📡" else "💤",
                        label = "GPS",
                        // Khi có h3Index từ server → cho thấy heartbeat đang hoạt động
                        value = when {
                            uiState.currentH3Index != null ->
                                "…${uiState.currentH3Index.takeLast(4)}"
                            uiState.isStreamingLocation -> "Live"
                            else -> "Off"
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Error / info banners
            uiState.errorMessage?.let { msg ->
                Text(
                    msg, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFEF5350)),
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }
            uiState.infoMessage?.let { msg ->
                Text(
                    msg, style = MaterialTheme.typography.bodySmall.copy(color = OnlineGreen),
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }

            // Action button
            if (uiState.isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DriverOrange)
                }
            } else {
                val needsVehicle = uiState.profile == null
                if (isOnline) {
                    OutlinedButton(
                        onClick = onToggle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350))
                    ) {
                        Text(
                            "Kết Thúc Ca",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            if (needsVehicle) showVehicleSheet = true else onToggle()
                        },
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
                            "🏍  Bắt Đầu Nhận Cuốc",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }

    // Vehicle info bottom sheet (first-time setup)
    if (showVehicleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showVehicleSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF1E1E1E)
        ) {
            VehicleSetupSheet(
                uiState = uiState,
                onVehiclePlateChange = onVehiclePlateChange,
                onVehicleModelChange = onVehicleModelChange,
                onVehicleColorChange = onVehicleColorChange,
                onLicenseNumberChange = onLicenseNumberChange,
                onConfirm = {
                    showVehicleSheet = false
                    onToggle()
                }
            )
        }
    }
}

// ── Stat chip ─────────────────────────────────────────────────────────────────
@Composable
private fun StatChip(emoji: String, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.45f))
        )
    }
}

// ── Vehicle setup sheet ───────────────────────────────────────────────────────
@Composable
private fun VehicleSetupSheet(
    uiState: DriverHomeUiState,
    onVehiclePlateChange: (String) -> Unit,
    onVehicleModelChange: (String) -> Unit,
    onVehicleColorChange: (String) -> Unit,
    onLicenseNumberChange: (String) -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Thông tin xe",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color.White)
        )
        Text(
            "Hoàn thành hồ sơ để nhận cuốc lần đầu",
            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.55f))
        )
        Spacer(Modifier.height(4.dp))

        listOf(
            Triple(uiState.vehiclePlate, onVehiclePlateChange, "Biển số xe (VD: 51G-123.45)"),
            Triple(uiState.vehicleModel, onVehicleModelChange, "Dòng xe (VD: Honda Air Blade)"),
            Triple(uiState.vehicleColor, onVehicleColorChange, "Màu xe"),
            Triple(uiState.licenseNumber, onLicenseNumberChange, "Số bằng lái (12 chữ số)")
        ).forEach { (value, onChange, label) ->
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text(label, color = Color.White.copy(alpha = 0.55f)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DriverOrange)
        ) {
            Text("Xác nhận & Quét mặt", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}
