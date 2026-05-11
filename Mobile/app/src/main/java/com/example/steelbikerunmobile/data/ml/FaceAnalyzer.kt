package com.example.steelbikerunmobile.data.ml

import android.graphics.PointF
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.steelbikerunmobile.domain.model.EarCalculator
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark

/**
 * FaceAnalyzer — CameraX ImageAnalysis.Analyzer tích hợp Google ML Kit.
 *
 * Phân tích từng frame camera để:
 * 1. Phát hiện khuôn mặt (Face Detection)
 * 2. Tính EAR (Eye Aspect Ratio) từ face landmarks
 * 3. Phát hiện chớp mắt (blink detection)
 * 4. Phát hiện buồn ngủ (mắt nhắm liên tục > 2 giây)
 *
 * Callback trả về [FaceAnalysisResult] mỗi frame để UI cập nhật trạng thái.
 */
class FaceAnalyzer(
    private val onResult: (FaceAnalysisResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.25f)
            .build()
    )

    // Đếm số frame liên tiếp mắt nhắm
    private var closedEyeFrames = 0

    // Đếm tổng số blink phát hiện được
    private var blinkCount = 0
    private var wasBlinking = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    onResult(FaceAnalysisResult.NoFace)
                    closedEyeFrames = 0
                    wasBlinking = false
                } else {
                    val face = faces[0]
                    processFace(face)
                }
            }
            .addOnFailureListener {
                onResult(FaceAnalysisResult.Error(it.message ?: "Detection failed"))
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun processFace(face: Face) {
        // ML Kit cung cấp classification trực tiếp cho mắt
        val leftEyeOpenProb = face.leftEyeOpenProbability
        val rightEyeOpenProb = face.rightEyeOpenProbability

        // Nếu ML Kit có classification
        if (leftEyeOpenProb != null && rightEyeOpenProb != null) {
            val leftEar = leftEyeOpenProb  // 0.0 = nhắm, 1.0 = mở
            val rightEar = rightEyeOpenProb

            val isBlinking = leftEar < 0.4f && rightEar < 0.4f

            if (isBlinking) {
                closedEyeFrames++
                if (!wasBlinking) {
                    blinkCount++
                    wasBlinking = true
                }
            } else {
                closedEyeFrames = 0
                wasBlinking = false
            }

            val isDrowsy = closedEyeFrames >= EarCalculator.DROWSY_CONSECUTIVE_FRAMES

            onResult(
                FaceAnalysisResult.Detected(
                    leftEar = leftEar,
                    rightEar = rightEar,
                    isBlinking = isBlinking,
                    isDrowsy = isDrowsy,
                    blinkCount = blinkCount,
                    smilingProb = face.smilingProbability ?: 0f,
                    headEulerY = face.headEulerAngleY,  // Quay trái/phải
                    headEulerZ = face.headEulerAngleZ,  // Nghiêng
                )
            )
        } else {
            // Fallback: dùng landmark để tính EAR
            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
            val leftCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK)?.position
            val rightCheek = face.getLandmark(FaceLandmark.RIGHT_CHEEK)?.position
            val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)?.position
            val leftEar = face.getLandmark(FaceLandmark.LEFT_EAR)?.position
            val rightEarLandmark = face.getLandmark(FaceLandmark.RIGHT_EAR)?.position

            if (leftEye != null && rightEye != null) {
                val leftEarVal = EarCalculator.estimateEar(leftEye, leftEar, leftCheek, noseBase)
                val rightEarVal = EarCalculator.estimateEar(rightEye, rightEarLandmark, rightCheek, noseBase)

                val isBlinking = EarCalculator.isBlinking(leftEarVal, rightEarVal)

                if (isBlinking) {
                    closedEyeFrames++
                    if (!wasBlinking) {
                        blinkCount++
                        wasBlinking = true
                    }
                } else {
                    closedEyeFrames = 0
                    wasBlinking = false
                }

                val isDrowsy = closedEyeFrames >= EarCalculator.DROWSY_CONSECUTIVE_FRAMES

                onResult(
                    FaceAnalysisResult.Detected(
                        leftEar = leftEarVal,
                        rightEar = rightEarVal,
                        isBlinking = isBlinking,
                        isDrowsy = isDrowsy,
                        blinkCount = blinkCount,
                        smilingProb = face.smilingProbability ?: 0f,
                        headEulerY = face.headEulerAngleY,
                        headEulerZ = face.headEulerAngleZ,
                    )
                )
            } else {
                onResult(FaceAnalysisResult.NoFace)
            }
        }
    }

    fun close() {
        detector.close()
    }
}

/**
 * Kết quả phân tích khuôn mặt cho mỗi frame camera.
 */
sealed interface FaceAnalysisResult {

    /** Không phát hiện khuôn mặt nào */
    data object NoFace : FaceAnalysisResult

    /** Phát hiện và phân tích thành công */
    data class Detected(
        val leftEar: Float,           // 0.0=nhắm, 1.0=mở (hoặc EAR value)
        val rightEar: Float,
        val isBlinking: Boolean,       // Cả 2 mắt đang nhắm
        val isDrowsy: Boolean,         // Nhắm mắt liên tục > ngưỡng
        val blinkCount: Int,           // Tổng lần chớp mắt
        val smilingProb: Float,        // Xác suất đang cười
        val headEulerY: Float,         // Quay đầu trái/phải
        val headEulerZ: Float,         // Nghiêng đầu
    ) : FaceAnalysisResult

    /** Lỗi trong quá trình phân tích */
    data class Error(val message: String) : FaceAnalysisResult
}
