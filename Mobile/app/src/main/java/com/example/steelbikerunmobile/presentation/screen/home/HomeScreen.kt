package com.example.steelbikerunmobile.presentation.screen.home

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.steelbikerunmobile.domain.model.UserRole
import com.example.steelbikerunmobile.presentation.screen.customer.home.CustomerHomeScreen
import com.example.steelbikerunmobile.presentation.screen.driver.home.DriverHomeScreen
import com.example.steelbikerunmobile.presentation.theme.AppThemeMode
import com.example.steelbikerunmobile.presentation.theme.SteelBikeTheme

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    onNavigateToProfile: (isDriverMode: Boolean) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // ── Permission state ───────────────────────────────────────────────────────
    var showRationale by remember { mutableStateOf(false) }
    var showSettingsPrompt by remember { mutableStateOf(false) }

    val hasLocationPermission = {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (!results.values.any { it }) {
            // Tất cả permission bị từ chối → kiểm tra có bị chặn vĩnh viễn không
            val isPermanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(
                context as Activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            if (isPermanentlyDenied) {
                // Người dùng đã chọn "Không hỏi lại" → chỉ có thể mở Settings
                showSettingsPrompt = true
            }
        }
    }

    // Yêu cầu quyền ngay khi người dùng đăng nhập thành công (session có giá trị)
    LaunchedEffect(session != null) {
        if (session != null && !hasLocationPermission()) {
            val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                context as Activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            if (shouldShowRationale) {
                // Đã từ chối 1 lần → giải thích lý do trước khi hỏi lại
                showRationale = true
            } else {
                // Lần đầu hỏi → request trực tiếp
                permissionLauncher.launch(LOCATION_PERMISSIONS)
            }
        }
    }

    // ── Rationale dialog (sau lần từ chối đầu tiên) ───────────────────────────
    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Cần quyền vị trí") },
            text = {
                Text(
                    "SteelBike cần vị trí GPS để hiển thị tài xế gần bạn và theo dõi hành trình theo thời gian thực.\n\n" +
                        "Tài xế cần GPS để gửi vị trí lên hệ thống (heartbeat mỗi 3 giây)."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionLauncher.launch(LOCATION_PERMISSIONS)
                }) {
                    Text("Cấp quyền")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) {
                    Text("Bỏ qua")
                }
            }
        )
    }

    // ── Settings prompt (bị chặn vĩnh viễn) ──────────────────────────────────
    if (showSettingsPrompt) {
        AlertDialog(
            onDismissRequest = { showSettingsPrompt = false },
            title = { Text("Quyền vị trí bị chặn") },
            text = {
                Text(
                    "Quyền vị trí đã bị chặn vĩnh viễn. Vui lòng vào Cài đặt ứng dụng để cấp quyền thủ công."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSettingsPrompt = false
                    // Mở trực tiếp trang cài đặt permission của app
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    )
                }) {
                    Text("Mở Cài đặt")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsPrompt = false }) {
                    Text("Để sau")
                }
            }
        )
    }

    // ── Main content ───────────────────────────────────────────────────────────
    when (session?.role) {
        UserRole.DRIVER -> {
            SteelBikeTheme(mode = AppThemeMode.DRIVER) {
                DriverHomeScreen(
                    onLogout = onLogout,
                    onNavigateToProfile = { onNavigateToProfile(true) },
                )
            }
        }
        UserRole.CUSTOMER, UserRole.ADMIN -> {
            CustomerHomeScreen(
                onLogout = onLogout,
                onNavigateToProfile = { onNavigateToProfile(false) },
            )
        }
        null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
