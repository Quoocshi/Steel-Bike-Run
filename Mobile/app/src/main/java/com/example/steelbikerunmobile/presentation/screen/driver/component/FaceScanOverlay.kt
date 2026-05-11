package com.example.steelbikerunmobile.presentation.screen.driver.component

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.steelbikerunmobile.data.ml.FaceAnalysisResult
import com.example.steelbikerunmobile.data.ml.FaceAnalyzer
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

// -- Face-scan state machine --
private enum class FaceScanState {
    IDLE, SCANNING, BLINK_PROMPT, PASSED, FAILED
}

private val NeonGreen = Color(0xFF00FF88)
private val ScanBlue = Color(0xFF4FC3F7)
private val FailRed = Color(0xFFEF5350)
private val BackgroundDark = Color(0xE6050A0E)

// -- Main composable --
@Composable
fun FaceScanOverlay(
    onPass: () -> Unit,
    onFail: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    var scanState by remember { mutableStateOf(FaceScanState.IDLE) }
    var blinkCount by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf("Đang khởi động camera...") }
    var faceDetected by remember { mutableStateOf(false) }

    // ML Kit FaceAnalyzer instance
    val faceAnalyzer = remember {
        FaceAnalyzer { result ->
            when (result) {
                is FaceAnalysisResult.NoFace -> {
                    faceDetected = false
                    if (scanState == FaceScanState.SCANNING || scanState == FaceScanState.BLINK_PROMPT) {
                        statusText = "Không nhận diện được khuôn mặt"
                    }
                }
                is FaceAnalysisResult.Detected -> {
                    faceDetected = true
                    blinkCount = result.blinkCount

                    when (scanState) {
                        FaceScanState.SCANNING -> {
                            // Khuôn mặt phát hiện + mắt mở -> chuyển sang yêu cầu chớp mắt
                            if (!result.isBlinking && result.leftEar > 0.3f) {
                                statusText = "Khuôn mặt OK! Đang kiểm tra..."
                            }
                        }
                        FaceScanState.BLINK_PROMPT -> {
                            statusText = "Vui lòng chớp mắt (${result.blinkCount} lần)"
                            if (result.isDrowsy) {
                                scanState = FaceScanState.FAILED
                                statusText = "Phát hiện mệt mỏi. Hãy nghỉ ngơi."
                            }
                        }
                        else -> { /* no-op */ }
                    }
                }
                is FaceAnalysisResult.Error -> {
                    statusText = "Lỗi: ${result.message}"
                }
            }
        }
    }

    // Cleanup ML Kit detector
    DisposableEffect(Unit) {
        onDispose { faceAnalyzer.close() }
    }

    // Face scan state machine flow
    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        scanState = FaceScanState.SCANNING
        statusText = "Đang kiểm tra khuôn mặt..."

        // Chờ phát hiện khuôn mặt (tối đa 5 giây)
        var waited = 0
        while (!faceDetected && waited < 50) {
            delay(100L)
            waited++
        }
        if (!faceDetected) {
            scanState = FaceScanState.FAILED
            statusText = "Không tìm thấy khuôn mặt. Hãy thử lại."
            delay(2_000L)
            onFail()
            return@LaunchedEffect
        }

        // Chuyển sang bước yêu cầu chớp mắt
        delay(1_500L)
        scanState = FaceScanState.BLINK_PROMPT
        statusText = "Vui lòng chớp mắt"
        val startBlinkCount = blinkCount

        // Chờ ít nhất 1 blink mới (tối đa 5 giây)
        waited = 0
        while (blinkCount <= startBlinkCount && waited < 50) {
            delay(100L)
            waited++
            // Kiểm tra nếu bị phát hiện buồn ngủ
            if (scanState == FaceScanState.FAILED) {
                delay(2_000L)
                onFail()
                return@LaunchedEffect
            }
        }

        if (blinkCount > startBlinkCount) {
            scanState = FaceScanState.PASSED
            statusText = "Tỉnh táo – Sẵn sàng nhận cuốc!"
            delay(1_400L)
            onPass()
        } else {
            scanState = FaceScanState.FAILED
            statusText = "Không phát hiện chớp mắt. Hãy thử lại."
            delay(2_000L)
            onFail()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark),
        contentAlignment = Alignment.Center
    ) {
        // Dot-grid background pattern
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dotColor = Color.White.copy(alpha = 0.04f)
            val spacing = 28.dp.toPx()
            var x = spacing / 2
            while (x < size.width) {
                var y = spacing / 2
                while (y < size.height) {
                    drawCircle(dotColor, 2f, Offset(x, y))
                    y += spacing
                }
                x += spacing
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Title
            Text(
                text = "XÁC MINH TÀI XẾ",
                style = MaterialTheme.typography.labelLarge.copy(
                    letterSpacing = 4.sp,
                    color = Color.White.copy(alpha = 0.55f)
                )
            )
            Spacer(Modifier.height(28.dp))

            // Camera circle + scan ring with ML Kit analysis
            CircularCameraFrame(
                scanState = scanState,
                hasCameraPermission = hasCameraPermission,
                lifecycleOwner = lifecycleOwner,
                faceAnalyzer = faceAnalyzer
            )

            Spacer(Modifier.height(32.dp))

            // Status text
            AnimatedContent(
                targetState = scanState,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "scanStatus"
            ) { state ->
                val color = when (state) {
                    FaceScanState.IDLE -> Color.White.copy(alpha = 0.6f)
                    FaceScanState.SCANNING -> ScanBlue
                    FaceScanState.BLINK_PROMPT -> Color(0xFFFFA726)
                    FaceScanState.PASSED -> NeonGreen
                    FaceScanState.FAILED -> FailRed
                }
                val icon = when (state) {
                    FaceScanState.PASSED -> "✓  "
                    FaceScanState.FAILED -> "✗  "
                    else -> ""
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$icon$statusText",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = color
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Pulsing status LED
            StatusLed(scanState = scanState)
        }

        // Close button
        IconButton(
            onClick = onFail,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Hủy", tint = Color.White.copy(alpha = 0.7f))
        }
    }
}

// -- Circular camera frame with ML Kit analysis --
@Composable
private fun CircularCameraFrame(
    scanState: FaceScanState,
    hasCameraPermission: Boolean,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    faceAnalyzer: FaceAnalyzer
) {
    val context = LocalContext.current
    val frameSize = 240.dp
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val rotArc1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "arc1"
    )
    val rotArc2 by infiniteTransition.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "arc2"
    )

    val glowAlpha = remember { Animatable(0f) }
    LaunchedEffect(scanState) {
        if (scanState == FaceScanState.PASSED) {
            while (true) {
                glowAlpha.animateTo(1f, tween(500))
                glowAlpha.animateTo(0.5f, tween(500))
            }
        } else {
            glowAlpha.snapTo(0f)
        }
    }

    val ringColor = when (scanState) {
        FaceScanState.PASSED -> NeonGreen
        FaceScanState.FAILED -> FailRed
        else -> ScanBlue
    }
    val isScanning = scanState == FaceScanState.SCANNING || scanState == FaceScanState.BLINK_PROMPT

    Box(
        modifier = Modifier.size(frameSize + 32.dp),
        contentAlignment = Alignment.Center
    ) {
        // Canvas for HUD rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2
            val outerR = size.minDimension / 2 - 4.dp.toPx()
            val innerR = outerR - 18.dp.toPx()
            val strokeW = 3.dp.toPx()
            val topLeft = Offset(cx - outerR, cy - outerR)
            val arcSize = Size(outerR * 2, outerR * 2)

            if (scanState == FaceScanState.PASSED) {
                for (i in 1..4) {
                    drawCircle(
                        color = NeonGreen.copy(alpha = 0.07f * (5 - i) * glowAlpha.value),
                        radius = outerR + (i * 9).dp.toPx(),
                        style = Stroke(3.dp.toPx())
                    )
                }
            }

            drawCircle(
                color = ringColor.copy(alpha = 0.30f),
                radius = outerR,
                style = Stroke(strokeW)
            )

            if (isScanning) {
                rotate(degrees = rotArc1, pivot = Offset(cx, cy)) {
                    drawArc(
                        color = ScanBlue.copy(alpha = 0.9f),
                        startAngle = -30f, sweepAngle = 100f,
                        useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(strokeW + 1.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                rotate(degrees = rotArc2, pivot = Offset(cx, cy)) {
                    drawArc(
                        color = ScanBlue.copy(alpha = 0.55f),
                        startAngle = 0f, sweepAngle = 60f,
                        useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(strokeW - 1.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            if (!isScanning) {
                drawCircle(color = ringColor, radius = outerR, style = Stroke(strokeW + 1.dp.toPx()))
            }

            // Corner HUD brackets
            val bracketLen = 20.dp.toPx()
            val bracketW = 4.dp.toPx()
            val bracketColor = ringColor.copy(alpha = 0.80f)
            val bR = innerR + 8.dp.toPx()
            drawLine(bracketColor, Offset(cx - bR, cy - bR), Offset(cx - bR + bracketLen, cy - bR), bracketW, StrokeCap.Round)
            drawLine(bracketColor, Offset(cx - bR, cy - bR), Offset(cx - bR, cy - bR + bracketLen), bracketW, StrokeCap.Round)
            drawLine(bracketColor, Offset(cx + bR, cy - bR), Offset(cx + bR - bracketLen, cy - bR), bracketW, StrokeCap.Round)
            drawLine(bracketColor, Offset(cx + bR, cy - bR), Offset(cx + bR, cy - bR + bracketLen), bracketW, StrokeCap.Round)
            drawLine(bracketColor, Offset(cx - bR, cy + bR), Offset(cx - bR + bracketLen, cy + bR), bracketW, StrokeCap.Round)
            drawLine(bracketColor, Offset(cx - bR, cy + bR), Offset(cx - bR, cy + bR - bracketLen), bracketW, StrokeCap.Round)
            drawLine(bracketColor, Offset(cx + bR, cy + bR), Offset(cx + bR - bracketLen, cy + bR), bracketW, StrokeCap.Round)
            drawLine(bracketColor, Offset(cx + bR, cy + bR), Offset(cx + bR, cy + bR - bracketLen), bracketW, StrokeCap.Round)
        }

        // CameraX preview + ML Kit analysis
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            val cameraProvider = providerFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(surfaceProvider)
                            }
                            // ML Kit image analysis
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { it.setAnalyzer(analysisExecutor, faceAnalyzer) }

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_FRONT_CAMERA,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (_: Exception) { }
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                modifier = Modifier
                    .size(frameSize)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(frameSize)
                    .clip(CircleShape)
                    .background(Color(0xFF1A1A2E)),
                contentAlignment = Alignment.Center
            ) {
                Text("📷", fontSize = 56.sp)
            }
        }
    }
}

// -- Pulsing status LED row --
@Composable
private fun StatusLed(scanState: FaceScanState) {
    val infiniteTransition = rememberInfiniteTransition(label = "led")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "ledAlpha"
    )
    val ledColor = when (scanState) {
        FaceScanState.SCANNING, FaceScanState.BLINK_PROMPT -> ScanBlue
        FaceScanState.PASSED -> NeonGreen
        FaceScanState.FAILED -> FailRed
        else -> Color.Gray
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            val dotAlpha = if (scanState == FaceScanState.SCANNING || scanState == FaceScanState.BLINK_PROMPT) {
                if (i == 0) alpha else if (i == 1) (alpha + 0.3f).coerceAtMost(1f) else 1f - alpha
            } else 1f
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(ledColor.copy(alpha = dotAlpha))
            )
        }
    }
}
