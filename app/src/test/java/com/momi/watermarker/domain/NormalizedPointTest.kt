package com.momi.watermarker.domain

import com.momi.watermarker.domain.model.NormalizedPoint
import com.momi.watermarker.domain.model.isUsableSelection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizedPointTest {

    @Test
    fun `a small cluster is not a usable selection`() {
        val points = List(12) { NormalizedPoint(0.50f + it * 0.001f, 0.50f) }
        assertFalse(points.isUsableSelection())
    }

    @Test
    fun `a traced rectangle is a usable selection`() {
        assertTrue(squareOutline().isUsableSelection())
    }

    @Test
    fun `fewer than eight points is not usable`() {
        val points = listOf(
            NormalizedPoint(0.1f, 0.1f),
            NormalizedPoint(0.9f, 0.1f),
            NormalizedPoint(0.9f, 0.9f),
            NormalizedPoint(0.1f, 0.9f),
        )
        assertFalse(points.isUsableSelection())
    }
}

internal fun squareOutline(): List<NormalizedPoint> {
    val points = mutableListOf<NormalizedPoint>()
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, steps: Int) {
        repeat(steps) { i ->
            val t = i / steps.toFloat()
            points += NormalizedPoint(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t)
        }
    }
    line(0.2f, 0.2f, 0.8f, 0.2f, 4)
    line(0.8f, 0.2f, 0.8f, 0.8f, 4)
    line(0.8f, 0.8f, 0.2f, 0.8f, 4)
    line(0.2f, 0.8f, 0.2f, 0.2f, 4)
    return points
}
