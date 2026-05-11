package com.example.steelbikerunmobile.domain.model

import android.graphics.PointF

/**
 * EAR Calculator — tính Eye Aspect Ratio để phát hiện nhắm mắt / buồn ngủ.
 *
 * Công thức EAR (Eye Aspect Ratio):
 * ```
 * EAR = (|P2 - P6| + |P3 - P5|) / (2 * |P1 - P4|)
 * ```
 *
 * Trong đó P1-P6 là 6 landmark xung quanh mắt:
 * ```
 *        P2   P3
 *   P1              P4
 *        P6   P5
 * ```
 *
 * - EAR ≈ 0.3 khi mắt mở bình thường
 * - EAR < 0.21 khi mắt đang nhắm (ngưỡng chớp mắt)
 * - Nếu EAR < 0.21 liên tục > 2 giây → nghi ngờ buồn ngủ
 */
object EarCalculator {

    /** Ngưỡng EAR dưới mức này = mắt nhắm */
    const val BLINK_THRESHOLD = 0.21f

    /** Số frame liên tiếp mắt nhắm → coi là buồn ngủ */
    const val DROWSY_CONSECUTIVE_FRAMES = 15  // ~2 giây ở 8 FPS

    /**
     * Tính EAR cho 1 mắt từ 6 điểm landmark.
     *
     * Google ML Kit FaceDetection cung cấp 5 landmarks chính cho mỗi bên mắt:
     * LEFT_EYE (tâm), LEFT_CHEEK... Nhưng KHÔNG cung cấp 6 điểm rìa mắt riêng lẻ.
     *
     * Trong thực tế, ML Kit chỉ trả tâm mắt trái (LEFT_EYE) và tâm mắt phải (RIGHT_EYE).
     * Do đó, chúng ta sử dụng phương pháp thay thế:
     * - Tính khoảng cách giữa 2 tâm mắt (interocular distance)
     * - So sánh với khoảng cách giữa tâm mắt và gò má (cheek) để ước lượng EAR
     *
     * @param eyeCenter     tâm mắt (LEFT_EYE hoặc RIGHT_EYE)
     * @param earBase       điểm tai cùng phía (LEFT_EAR hoặc RIGHT_EAR), dùng làm reference ngang
     * @param cheek         điểm gò má cùng phía, dùng làm reference dọc
     * @param noseBase      điểm mũi, dùng normalize khoảng cách
     * @return EAR ước tính (0.0 = mắt nhắm hoàn toàn, ~0.3 = mắt mở bình thường)
     */
    fun estimateEar(
        eyeCenter: PointF,
        earBase: PointF?,
        cheek: PointF?,
        noseBase: PointF?
    ): Float {
        if (cheek == null || noseBase == null) return 0.3f  // Fallback: coi như mở mắt

        // Khoảng cách dọc: từ tâm mắt đến gò má (nhỏ khi mắt nhắm, lớn khi mở)
        val verticalDist = distance(eyeCenter, cheek)

        // Khoảng cách ngang: từ mũi đến tai (reference distance để normalize)
        val horizontalDist = if (earBase != null) {
            distance(noseBase, earBase)
        } else {
            distance(eyeCenter, noseBase) * 2  // Ước lượng
        }

        if (horizontalDist < 1f) return 0.3f  // Tránh chia cho 0

        // EAR thô = tỷ lệ dọc/ngang, nhân hệ số calibration
        val rawEar = (verticalDist / horizontalDist) * 1.8f
        return rawEar.coerceIn(0f, 0.5f)
    }

    /**
     * Phát hiện chớp mắt: cả hai mắt có EAR dưới ngưỡng.
     */
    fun isBlinking(leftEar: Float, rightEar: Float): Boolean {
        return leftEar < BLINK_THRESHOLD && rightEar < BLINK_THRESHOLD
    }

    /**
     * Khoảng cách Euclidean giữa 2 điểm.
     */
    private fun distance(a: PointF, b: PointF): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
