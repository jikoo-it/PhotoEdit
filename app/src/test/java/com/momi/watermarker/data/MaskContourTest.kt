package com.momi.watermarker.data

import com.momi.watermarker.data.rendering.MaskContour
import com.momi.watermarker.domain.model.NormalizedPoint
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskContourTest {

    @Test
    fun `traces the outer edge of a filled rectangle`() {
        val width = 10
        val height = 10
        val mask = BooleanArray(width * height)
        for (y in 2..6) {
            for (x in 2..7) {
                mask[y * width + x] = true
            }
        }
        val contour = MaskContour.trace(mask, width, height)
        assertTrue("contour too short: ${contour.size}", contour.size >= 8)
        assertTrue(contour.all { (x, y) -> x in 2..7 && y in 2..6 })
    }

    @Test
    fun `simplify keeps a usable rectangle`() {
        val dense = mutableListOf<NormalizedPoint>()
        fun line(x0: Float, y0: Float, x1: Float, y1: Float, steps: Int) {
            repeat(steps) { i ->
                val t = i / steps.toFloat()
                dense += NormalizedPoint(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t)
            }
        }
        line(0.2f, 0.2f, 0.8f, 0.2f, 20)
        line(0.8f, 0.2f, 0.8f, 0.8f, 20)
        line(0.8f, 0.8f, 0.2f, 0.8f, 20)
        line(0.2f, 0.8f, 0.2f, 0.2f, 20)
        val simplified = MaskContour.simplify(dense, epsilon = 0.01f)
        assertTrue(simplified.size in 4..20)
    }

    @Test
    fun `empty mask yields no contour`() {
        val mask = BooleanArray(16)
        assertTrue(MaskContour.trace(mask, 4, 4).isEmpty())
    }
}
