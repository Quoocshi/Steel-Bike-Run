package com.example.steelbikerunmobile.domain.model

/**
 * EAR Calculator — tính Eye Aspect Ratio để phát hiện nhắm mắt / buồn ngủ.
 */
object EarCalculator {

    data class Point(val x: Float, val y: Float)

    /** Ngưỡng EAR dưới mức này = mắt nhắm */
    const val BLINK_THRESHOLD = 0.21f

    /** Số frame liên tiếp mắt nhắm → coi là buồn ngủ */
    const val DROWSY_CONSECUTIVE_FRAMES = 15  // ~2 giây ở 8 FPS

    /**
     * Tính EAR cho 1 mắt từ 6 điểm landmark.
     */
    fun estimateEar(
        eyeCenter: Point,
        earBase: Point?,
        cheek: Point?,
        noseBase: Point?
    ): Float {
        if (cheek == null || noseBase == null) return 0.3f

        val verticalDist = distance(eyeCenter, cheek)
        val horizontalDist = if (earBase != null) {
            distance(noseBase, earBase)
        } else {
            distance(eyeCenter, noseBase) * 2
        }

        if (horizontalDist < 1f) return 0.3f

        val rawEar = (verticalDist / horizontalDist) * 1.8f
        return rawEar.coerceIn(0f, 0.5f)
    }

    fun isBlinking(leftEar: Float, rightEar: Float): Boolean {
        return leftEar < BLINK_THRESHOLD && rightEar < BLINK_THRESHOLD
    }

    private fun distance(a: Point, b: Point): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
