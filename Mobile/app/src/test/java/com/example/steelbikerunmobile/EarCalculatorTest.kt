package com.example.steelbikerunmobile

import com.example.steelbikerunmobile.domain.model.EarCalculator
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit test cho EarCalculator.
 * Kiểm tra logic tính toán tỷ lệ mở mắt (EAR).
 */
class EarCalculatorTest {

    @Test
    fun testEstimateEar_OpenEye() {
        // Mock dữ liệu cho mắt mở (khoảng cách dọc lớn)
        val eyeCenter = EarCalculator.Point(100f, 100f)
        val earBase = EarCalculator.Point(150f, 100f)
        val cheek = EarCalculator.Point(100f, 115f) // Dọc = 15
        val noseBase = EarCalculator.Point(120f, 110f) // Ngang ~ 30

        val ear = EarCalculator.estimateEar(eyeCenter, earBase, cheek, noseBase)
        
        // EAR nên > 0.21 (blink threshold)
        assertTrue("EAR should be > 0.21 for open eyes, but was $ear", ear > 0.21f)
    }

    @Test
    fun testEstimateEar_ClosedEye() {
        // Mock dữ liệu cho mắt nhắm (khoảng cách dọc nhỏ)
        val eyeCenter = EarCalculator.Point(100f, 100f)
        val earBase = EarCalculator.Point(150f, 100f)
        val cheek = EarCalculator.Point(100f, 102f) // Dọc = 2
        val noseBase = EarCalculator.Point(120f, 110f)

        val ear = EarCalculator.estimateEar(eyeCenter, earBase, cheek, noseBase)
        
        // EAR nên < 0.21
        assertTrue("EAR should be < 0.21 for closed eyes, but was $ear", ear < 0.21f)
    }

    @Test
    fun testIsBlinking() {
        // Cả 2 mắt dưới ngưỡng
        assertTrue(EarCalculator.isBlinking(0.15f, 0.18f))
        
        // Một mắt mở, một mắt nhắm -> không coi là blink (theo logic app)
        assertFalse(EarCalculator.isBlinking(0.3f, 0.15f))
        
        // Cả 2 mắt mở
        assertFalse(EarCalculator.isBlinking(0.28f, 0.32f))
    }
}
